import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Main {

    static final String REG = "RA2311056010070";
    static final String BASE = "https://devapigw.vidalhealthtpa.com/srm-quiz-task";
    static final int ROUNDS = 10;
    static final long GAP = 5000;

    static PrintWriter pw;

    static void out(String s) {
        System.out.println(s);
        if (pw != null) { pw.println(s); pw.flush(); }
    }

    static void outf(String f, Object... a) {
        out(String.format(f, a));
    }

    static void bar(int cur, int max, int total, int uniq, int dup) {
        int w = 20;
        int f = (int)((double) cur / max * w);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < w; i++) b.append(i < f ? '\u2588' : '\u2591');
        System.out.printf("\r[%s] %d/%d | Total: %d | Unique: %d | Skipped: %d", b, cur, max, total, uniq, dup);
    }

    public static void main(String[] args) {
        HttpClient cl = HttpClient.newHttpClient();
        Gson pretty = new GsonBuilder().setPrettyPrinting().create();
        Gson g = new Gson();

        Set<String> tracker = new HashSet<>();
        Map<String, Integer> tally = new HashMap<>();
        List<List<Map<String, Object>>> rawPolls = new ArrayList<>();

        int evtCount = 0;
        int dupCount = 0;

        try {
            pw = new PrintWriter(new FileWriter("run_log.txt"));
            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            out("Run started: " + ts);
            outf("Reg: %s", REG);
            out("");

            for (int i = 0; i < ROUNDS; i++) {
                outf("[%d/%d] Fetching...", i + 1, ROUNDS);

                String url = String.format("%s/quiz/messages?regNo=%s&poll=%d", BASE, REG, i);
                HttpRequest rq = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
                HttpResponse<String> rs = cl.send(rq, HttpResponse.BodyHandlers.ofString());

                if (pw != null) { pw.println("  Raw: " + rs.body()); pw.flush(); }

                JsonObject root = g.fromJson(rs.body(), JsonObject.class);
                JsonArray evts = root.getAsJsonArray("events");

                List<Map<String, Object>> pollBatch = new ArrayList<>();
                for (JsonElement el : evts) {
                    JsonObject o = el.getAsJsonObject();
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("roundId", o.get("roundId").getAsString());
                    m.put("participant", o.get("participant").getAsString());
                    m.put("score", o.get("score").getAsInt());
                    pollBatch.add(m);
                }
                rawPolls.add(pollBatch);

                for (JsonElement el : evts) {
                    JsonObject o = el.getAsJsonObject();
                    String rid = o.get("roundId").getAsString();
                    String pname = o.get("participant").getAsString();
                    int pts = o.get("score").getAsInt();

                    evtCount++;
                    String k = rid + "|" + pname;

                    if (!tracker.add(k)) {
                        dupCount++;
                        outf("  SKIP %s (%s)", pname, rid);
                        continue;
                    }

                    tally.merge(pname, pts, Integer::sum);
                    outf("  +%d %s [%s]", pts, pname, rid);
                }

                bar(i + 1, ROUNDS, evtCount, evtCount - dupCount, dupCount);
                System.out.println();

                if (i < ROUNDS - 1) {
                    outf("  delay %dms\n", GAP);
                    Thread.sleep(GAP);
                }
            }

            List<Map.Entry<String, Integer>> sorted = new ArrayList<>(tally.entrySet());
            sorted.sort(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue).reversed());

            int grand = tally.values().stream().mapToInt(Integer::intValue).sum();

            int nameW = sorted.stream().mapToInt(e -> e.getKey().length()).max().orElse(12);
            nameW = Math.max(nameW, 11);
            int scoreW = 12;
            int tblW = nameW + scoreW + 5;

            String nd = "\u2550".repeat(nameW + 2);
            String sd = "\u2550".repeat(scoreW + 2);

            out("");
            out("\u2554" + nd + "\u2566" + sd + "\u2557");
            outf("\u2551 %-" + nameW + "s \u2551 %-" + scoreW + "s \u2551", "PARTICIPANT", "TOTAL SCORE");
            out("\u2560" + nd + "\u256C" + sd + "\u2563");

            int rank = 1;
            for (Map.Entry<String, Integer> e : sorted) {
                outf("\u2551 %-" + nameW + "s \u2551 %" + scoreW + "d \u2551", "#" + rank++ + " " + e.getKey(), e.getValue());
            }

            out("\u2560" + nd + "\u2569" + sd + "\u2563");
            outf("\u2551 GRAND TOTAL: %-" + (tblW - 16) + "d \u2551", grand);
            out("\u255A" + "\u2550".repeat(tblW) + "\u255D");

            out("");
            outf("Events: %d | Unique: %d | Duplicates: %d", evtCount, tracker.size(), dupCount);

            List<Map<String, Object>> lb = new ArrayList<>();
            for (Map.Entry<String, Integer> e : sorted) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("participant", e.getKey());
                row.put("totalScore", e.getValue());
                lb.add(row);
            }

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("regNo", REG);
            payload.put("leaderboard", lb);

            String body = g.toJson(payload);
            out("\nSubmitting...");
            out(body);

            HttpRequest pr = HttpRequest.newBuilder()
                    .uri(URI.create(BASE + "/quiz/submit"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> sr = cl.send(pr, HttpResponse.BodyHandlers.ofString());
            out("\nResponse:");
            out(sr.body());

            Files.writeString(Path.of("result.json"),
                    pretty.toJson(g.fromJson(sr.body(), JsonObject.class)));

            Map<String, Object> dbd = new LinkedHashMap<>();
            dbd.put("regNo", REG);
            dbd.put("timestamp", ts);
            dbd.put("polls", rawPolls);
            dbd.put("submitResponse", g.fromJson(sr.body(), JsonObject.class));
            Files.writeString(Path.of("poll_data.json"), pretty.toJson(dbd));

            out("\nDone.");

        } catch (Exception ex) {
            System.err.println(ex.getMessage());
            ex.printStackTrace();
        } finally {
            if (pw != null) pw.close();
        }
    }
}
