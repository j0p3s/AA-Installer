/*
 * AA Installer - install Android Auto apps as Play Store apps
 * Copyright (C) 2026 j0p3s
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.github.j0p3s.aainstaller;

import android.os.ParcelFileDescriptor;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Runs inside a separate process started by Shizuku with the shell UID (2000),
 * i.e. the same privileges as "adb shell". This is what lets us run
 * "pm install -i com.android.vending" without a PC.
 */
public class ShellService extends IShellService.Stub {

    public ShellService() {
    }

    @Override
    public void destroy() {
        System.exit(0);
    }

    @Override
    public String run(String[] cmd, ParcelFileDescriptor stdin) {
        return exec(cmd, stdin == null ? null : new ParcelFileDescriptor.AutoCloseInputStream(stdin));
    }

    static String exec(String[] cmd, InputStream stdin) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();

            Thread feeder = null;
            if (stdin != null) {
                feeder = new Thread(() -> {
                    try (InputStream in = stdin; OutputStream out = p.getOutputStream()) {
                        byte[] buf = new byte[1 << 16];
                        int n;
                        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                        out.flush();
                    } catch (Exception ignored) {
                    }
                });
                feeder.start();
            } else {
                p.getOutputStream().close();
            }

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (InputStream out = p.getInputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = out.read(buf)) > 0) bos.write(buf, 0, n);
            }
            int code = p.waitFor();
            if (feeder != null) feeder.join(5000);
            return code + "\n" + bos.toString("UTF-8").trim();
        } catch (Throwable t) {
            return "255\n" + t;
        }
    }
}
