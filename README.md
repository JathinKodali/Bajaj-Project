# Quiz Leaderboard System

## Problem Statement
Poll a remote quiz API across 10 sequential rounds, collect event data,
eliminate duplicate entries using a composite key of `roundId` and `participant`,
accumulate scores per participant, and submit the final sorted leaderboard via POST.

## Tech Stack
- **Language:** Java 17
- **JSON Library:** Gson 2.10.1
- **HTTP:** `java.net.http.HttpClient` (built-in)

## Project Structure
```
├── src/
│   └── Main.java          # Application entry point
├── lib/
│   └── gson-2.10.1.jar    # JSON parsing dependency
├── dashboard.html          # Visual replay dashboard
├── .gitignore
└── README.md
```

## How to Run

### 1. Set Your Registration Number
Open `src/Main.java` and update `REG` with your actual registration number.

### 2. Compile
```bash
javac -cp lib/gson-2.10.1.jar src/Main.java -d out/
```

### 3. Run
```bash
java -cp out:lib/gson-2.10.1.jar Main
```
> **Windows users:** Replace `:` with `;` in the classpath.

### 4. View Dashboard (optional)
```bash
python3 -m http.server 8080
# Open http://localhost:8080/dashboard.html
```

## Approach

1. **Polling** — Sequentially hits `GET /quiz/messages?regNo=...&poll=N` for N = 0 to 9 with a mandatory 5-second delay between each request using `Thread.sleep(5000)`.
2. **Deduplication** — Each event is keyed by `roundId|participant`. A `HashSet` ensures that if the same `(roundId, participant)` pair appears in a later poll, it is ignored and not double-counted.
3. **Aggregation** — A `HashMap<String, Integer>` accumulates total scores per participant using `merge()`. Only non-duplicate events contribute to the score.
4. **Ranking** — After all 10 polls, entries are sorted in descending order by `totalScore`.
5. **Submission** — A single `POST /quiz/submit` is made once after all polls complete, containing the sorted leaderboard array.

## Requirements Verification

| # | Requirement | Status | Implementation |
|---|---|---|---|
| 1 | 10 polls must be executed | ✅ Verified | Loop from `poll=0` to `poll=9` (`ROUNDS = 10`) |
| 2 | Duplicate API response data must be handled correctly | ✅ Verified | `HashSet<String>` tracks `roundId\|participant` keys; duplicates are skipped via `tracker.add(k)` returning false |
| 3 | Leaderboard must be correct | ✅ Verified | Sorted descending by `totalScore` using `Comparator.comparingInt().reversed()` |
| 4 | Total score must be correct | ✅ Verified | Only unique events are aggregated via `tally.merge(pname, pts, Integer::sum)` |
| 5 | Submit only once | ✅ Verified | Single `POST /quiz/submit` after the polling loop ends |
| 6 | 5-second delay between polls | ✅ Verified | `Thread.sleep(5000)` enforced between each poll request |

## Duplicate Handling Example
```
Poll 0 → Diana +200 (R1) → NEW, added to tracker
Poll 3 → Diana +200 (R1) → key "R1|Diana" already in HashSet → SKIPPED
```
This ensures each `(roundId, participant)` combination is counted exactly once.
