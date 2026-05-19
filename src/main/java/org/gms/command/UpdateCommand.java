package org.gms.command;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(name = "update", description = "更新 beidou 到最新版本")
public class UpdateCommand implements Callable<Integer> {

    @Option(names = {"-h", "--help"}, usageHelp = true, description = "显示帮助信息")
    boolean help;

    private static final String BASE = "https://github.com/BeiDouMS/beidou-cli/releases/latest/download";
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @Override
    public Integer call() {
        // Find current binary
        var currentPath = findCurrentBinary();
        if (currentPath == null) {
            System.err.println("未找到 beidou 二进制文件，请先安装");
            return 1;
        }
        System.out.println("当前安装: " + currentPath);

        // Detect platform
        String platform;
        var os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) platform = "windows";
        else if (os.contains("mac")) platform = "macos";
        else platform = "linux";

        String ext = platform.equals("windows") ? ".exe" : "";
        String url = BASE + "/beidou-" + platform + ext;

        System.out.println("下载: " + url);
        try {
            var tmp = Files.createTempFile("beidou", ext);
            var request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            var response = HTTP.send(request, HttpResponse.BodyHandlers.ofFile(tmp));
            if (response.statusCode() != 200) {
                Files.deleteIfExists(tmp);
                System.err.println("下载失败: HTTP " + response.statusCode());
                return 1;
            }
            Files.move(tmp, currentPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            if (!currentPath.toFile().setExecutable(true)) {
                System.err.println("[WARN] 无法设置可执行权限");
            }
            System.out.println("已更新: " + currentPath);
            return 0;
        } catch (IOException | InterruptedException e) {
            System.err.println("更新失败: " + e.getMessage());
            return 1;
        }
    }

    static Path findCurrentBinary() {
        var name = System.getProperty("os.name").toLowerCase().contains("win") ? "beidou.exe" : "beidou";
        for (var dir : System.getenv("PATH").split(System.getProperty("path.separator"))) {
            var candidate = Path.of(dir, name);
            if (Files.isRegularFile(candidate)) return candidate.toAbsolutePath();
        }
        return null;
    }
}
