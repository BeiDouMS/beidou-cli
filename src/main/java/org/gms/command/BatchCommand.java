package org.gms.command;

import org.gms.config.CliConfig;
import org.gms.http.ApiClient;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.concurrent.Callable;

@Command(name = "batch", description = "批量执行 API 请求，从 stdin 逐行读取")
public class BatchCommand implements Callable<Integer> {

    @Option(names = {"-h", "--help"}, usageHelp = true, description = "显示帮助信息")
    boolean help;

    @Override
    public Integer call() {
        var config = CliConfig.load();
        if (!config.isConfigured()) {
            System.err.println("未配置，请先执行 beidou config --server <url> --username <user> --password <pass>");
            return 1;
        }

        var client = new ApiClient(config);
        var reader = new BufferedReader(new InputStreamReader(System.in));
        var lines = new ArrayList<String>();

        try {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                lines.add(line);
            }
        } catch (Exception e) {
            System.err.println("读取 stdin 失败: " + e.getMessage());
            return 1;
        }

        if (lines.isEmpty()) {
            System.err.println("stdin 为空，无请求可执行");
            System.err.println("用法示例: echo 'GET /server/v1/online' | beidou batch");
            return 1;
        }

        int ok = 0, fail = 0;
        for (int i = 0; i < lines.size(); i++) {
            var parts = lines.get(i).trim().split("\\s+", 3);
            if (parts.length < 2) {
                System.err.println("[" + (i + 1) + "/" + lines.size() + "] 跳过无效行: " + lines.get(i));
                fail++;
                continue;
            }
            var method = parts[0];
            var path = parts[1];
            var body = parts.length > 2 ? parts[2] : null;
            path = fixMsysPath(path);

            System.out.println("── [" + (i + 1) + "/" + lines.size() + "] " + method + " " + path);
            try {
                client.callForBatch(method, path, body);
                ok++;
            } catch (Exception e) {
                System.err.println("[FAIL] " + e.getMessage());
                fail++;
            }
            System.out.println();
        }
        System.out.println("完成: " + ok + " 成功, " + fail + " 失败");
        return fail > 0 ? 1 : 0;
    }

    static String fixMsysPath(String path) {
        if (path.matches("^[A-Za-z]:/.+")) {
            var fixed = path.replaceFirst("^[A-Za-z]:.*?/(server|auth|character|common|cashShop|drop|gachapon|give|inventory|shop|config|command|file|autoban)/", "/$1/");
            if (!fixed.equals(path)) return fixed;
        }
        return path;
    }
}
