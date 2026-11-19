package io.github.j0p3s.aainstaller;

interface IShellService {
    // Reserved transaction code required by Shizuku UserService
    void destroy() = 16777114;

    // Runs a command as the shell user (same as adb). Returns "<exitCode>\n<output>".
    String run(in String[] cmd, in ParcelFileDescriptor stdin) = 1;
}
