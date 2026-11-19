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

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import rikka.shizuku.Shizuku;

/** Something that can run a command with shell (adb) or root privileges. */
public abstract class Shell {

    public static final class Result {
        public final int code;
        public final String out;

        Result(String raw) {
            int nl = raw.indexOf('\n');
            int c;
            try {
                c = Integer.parseInt(nl < 0 ? raw.trim() : raw.substring(0, nl).trim());
            } catch (NumberFormatException e) {
                c = 255;
            }
            code = c;
            out = nl < 0 ? "" : raw.substring(nl + 1);
        }

        public boolean ok() {
            return code == 0;
        }
    }

    public abstract String name();

    public abstract Result run(String[] cmd, InputStream stdin) throws Exception;

    public Result run(String... cmd) throws Exception {
        return run(cmd, null);
    }

    // ------------------------------------------------------------------ Shizuku

    public static boolean shizukuRunning() {
        try {
            return Shizuku.pingBinder() && !Shizuku.isPreV11();
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean shizukuGranted() {
        try {
            return shizukuRunning() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Shell backed by a Shizuku UserService running as uid 2000 (shell) or 0 (if Shizuku runs as root). */
    public static final class ShizukuShell extends Shell {
        private static ShizukuShell instance;
        private final IShellService service;
        private final int uid;

        private ShizukuShell(IShellService s, int uid) {
            this.service = s;
            this.uid = uid;
        }

        @Override
        public String name() {
            return uid == 0 ? "Shizuku (root)" : "Shizuku (adb)";
        }

        static Shizuku.UserServiceArgs args() {
            return new Shizuku.UserServiceArgs(
                    new ComponentName(BuildConfig.APPLICATION_ID, ShellService.class.getName()))
                    .daemon(false)
                    .processNameSuffix("shell")
                    .debuggable(BuildConfig.DEBUG)
                    .version(BuildConfig.VERSION_CODE);
        }

        /** Must NOT be called on the main thread. */
        public static synchronized ShizukuShell get() throws Exception {
            if (instance != null && instance.service.asBinder().pingBinder()) return instance;
            instance = null;
            final IShellService[] holder = new IShellService[1];
            final CountDownLatch latch = new CountDownLatch(1);
            ServiceConnection conn = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder binder) {
                    if (binder != null && binder.pingBinder()) holder[0] = IShellService.Stub.asInterface(binder);
                    latch.countDown();
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    instance = null;
                }
            };
            Shizuku.bindUserService(args(), conn);
            if (!latch.await(15, TimeUnit.SECONDS) || holder[0] == null)
                throw new IllegalStateException("Could not start the Shizuku shell service. Is Shizuku running?");
            instance = new ShizukuShell(holder[0], Shizuku.getUid());
            return instance;
        }

        @Override
        public Result run(String[] cmd, InputStream stdin) throws Exception {
            if (stdin == null) return new Result(service.run(cmd, null));
            ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            Thread feeder = new Thread(() -> {
                try (InputStream in = stdin;
                     OutputStream out = new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])) {
                    byte[] buf = new byte[1 << 16];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                } catch (Exception ignored) {
                }
            });
            feeder.start();
            try {
                return new Result(service.run(cmd, pipe[0]));
            } finally {
                try {
                    pipe[0].close();
                } catch (Exception ignored) {
                }
                feeder.join(5000);
            }
        }
    }

    // --------------------------------------------------------------------- Root

    public static final class RootShell extends Shell {
        @Override
        public String name() {
            return "Root (su)";
        }

        public static boolean available() {
            try {
                Result r = new Result(ShellService.exec(new String[]{"su", "-c", "id"}, null));
                return r.ok() && r.out.contains("uid=0");
            } catch (Throwable t) {
                return false;
            }
        }

        @Override
        public Result run(String[] cmd, InputStream stdin) {
            StringBuilder sb = new StringBuilder();
            for (String a : cmd) {
                if (sb.length() > 0) sb.append(' ');
                sb.append('\'').append(a.replace("'", "'\\''")).append('\'');
            }
            return new Result(ShellService.exec(new String[]{"su", "-c", sb.toString()}, stdin));
        }
    }
}
