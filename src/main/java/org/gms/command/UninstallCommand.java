package org.gms.command;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(name = "uninstall", description = "卸载 beidou")
public class UninstallCommand implements Callable<Integer> {

    @Option(names = {"-h", "--help"}, usageHelp = true, description = "显示帮助信息")
    boolean help;

    @Override
    public Integer call() {
        var current = UpdateCommand.findCurrentBinary();
        if (current == null) {
            System.out.println("beidou 未安装或不在 PATH 中，无需卸载");
            return 0;
        }

        // 1. Remove binary
        try {
            Files.deleteIfExists(current);
            System.out.println("[OK] 已删除: " + current);
        } catch (IOException e) {
            System.err.println("[WARN] 删除失败: " + e.getMessage());
        }

        // 2. Clean PATH
        var dir = current.getParent().toString();
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            cleanWindowsPath(dir);
        } else {
            cleanUnixPath(dir);
        }
        System.out.println("卸载完成");
        return 0;
    }

    private void cleanUnixPath(String dir) {
        for (var rc : new String[] {".bashrc", ".zshrc", ".profile"}) {
            var file = Path.of(System.getProperty("user.home"), rc);
            try {
                if (!Files.isRegularFile(file)) continue;
                var content = Files.readString(file);
                var updated = content.replaceAll("(?m)^export PATH=\"?" + java.util.regex.Pattern.quote(dir) + "[:].*$\n?", "")
                                     .replaceAll("(?m)^export PATH=\"?" + java.util.regex.Pattern.quote(dir) + "\"?$\n?", "");
                if (!updated.equals(content)) {
                    Files.writeString(file, updated);
                    System.out.println("[OK] 已从 " + rc + " 移除 PATH 条目");
                }
            } catch (IOException ignored) {}
        }
    }

    private void cleanWindowsPath(String dir) {
        try {
            var pb = new ProcessBuilder(
                "powershell", "-NoProfile", "-Command",
                "$r='HKCU:\\Environment'; $p=(Get-ItemProperty -Path $r -Name PATH -EA SilentlyContinue).PATH; " +
                "if($p){ $n=($p.Split(';') | ?{$_ -ne '' -and $_ -ne '" + dir.replace("\\", "\\\\") + "'}) -join ';'; " +
                "Set-ItemProperty -Path $r -Name PATH -Value $n }"
            );
            pb.inheritIO().start().waitFor();
            System.out.println("[OK] 已从注册表移除 PATH 条目");
        } catch (Exception e) {
            System.err.println("[WARN] 清理注册表失败: " + e.getMessage());
            System.err.println("       手动执行: setx PATH \"...\" 去掉 " + dir);
        }
    }
}
