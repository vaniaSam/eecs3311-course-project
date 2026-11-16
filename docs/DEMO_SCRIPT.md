# Feature Demonstration Recording Script

The Stage 2 instructions require a running demonstration of **every** feature, showing both the GUI
and the CLI. The videos must be recorded by the student on a machine with a real LLM key: the
development container has no display recorder with narration and cannot reach an LLM provider.

## Before recording

1. `cp .env.example .env`, put your real key in `.env`. **Never show `.env` on screen.** Close any
   editor tab that contains it. The status bar shows only the model name (e.g. `openai/gpt-oss-20b via api.groq.com`).
2. `mvn package` then start the app fresh for each recording (the sample pantry dates are relative to today).
3. Use OBS Studio, the Windows Game Bar (Win+G), or macOS Screenshot (Cmd+Shift+5) with microphone on.
4. Recommended: **Video 1 = GUI (F01–F12)**, **Video 2 = CLI (F01–F12)**, about 10–12 min each.
   Upload as *Unlisted* on YouTube (or a OneDrive/Google Drive link with "anyone with the link can view")
   and test the links in a private browser window.
5. Write down the start time of each segment and copy it into the table at the end and into
   section 7 of `stage2-report.docx` (then re-export the PDF). YouTube timestamp links look like
   `https://youtu.be/VIDEO_ID?t=95`.

For every segment say: the **feature ID and name**, **what you type/click**, **what the result means**,
**where AI is involved** (or that it is deterministic), and show **one error/alternative case**.

## Video 1 – GUI (`java -jar target/meal-planner.jar`)

| # | Feature | Actions | Say / point out | Error / alternative case |
|---|---------|---------|-----------------|--------------------------|
| 0 | Intro | Show the window and the status bar | "AI enabled: openai/gpt-oss-20b via api.groq.com" — key comes from local `.env`, not shown | – |
| 1 | F01 Profile | Profile tab: budget `80`, preferences `quick meals, asian`, restrictions `vegetarian` → Save profile | Deterministic; values used by every planning feature | Budget `-5` → error, profile unchanged |
| 2 | F02 Pantry | Pantry tab: add `tofu`, `300`, `g`, expiry `+5`; click *Expiring within days* (3), *Show leftovers*, *Show expired* | Status column marks EXPIRED (yogurt) and *expires soon*; expired stock never counts | Quantity `abc` → "Quantity must be a number" |
| 3 | F03 Search | Recipes tab: keyword `pasta` → *Keyword search*; type `spinach, egg` → *Search by ingredients*; *Use my pantry* | Cookable recipes first, missing ingredients listed; deterministic, no AI | Keyword `zzz` → "No recipes match" |
| 4 | F07 Scale | Select *Red Lentil Soup*, servings 8 → *Scale (F07)* | Quantities and cost recalculated, original unchanged | Not selecting a recipe → error message |
| 5 | F08 Substitutes | Select *Spinach Omelette*, ingredient `milk` → *Suggest substitutes* | Curated substitutes are retrieved and sent to the LLM; proposals checked against restrictions; "[in your pantry]" marks | Set restriction `vegan` first: unsuitable items marked *NOT SUITABLE* |
| 6 | F04 Recommend | AI Planner tab, priority `pantry` → *Recommend recipes*; switch to `budget` and repeat | Strategy pattern changes the ranking; LLM picks and explains; agent steps show discarded ids if any | Explain notes about excluded recipes (restrictions) |
| 7 | F05 Generate recipe | Instruction `high-protein vegetarian lunch using spinach` → *Generate AI recipe* → *Save generated recipe* | LLM output parsed to a Recipe and validated; revision attempts appear in agent steps | Show the "AI-generated, verify allergens" warning |
| 8 | F06 Weekly plan | Days 5, Breakfast + Dinner, priority budget → *Generate weekly plan* | Agent steps: rank → LLM proposal → validation → revision; plan accepted only when valid | Budget `5` in profile → plan NOT accepted, error explains budget |
| 9 | F09 Leftovers | *Plan from leftovers* | Uses non-expired leftovers (cooked rice, roast chicken); LLM chooses catalogue recipe or creates a new one | Explain expired leftovers are never suggested |
| 10 | F10 Shopping list | Meal Plan tab: show plan and shopping list | Deterministic consolidation, pantry subtracted, note on unit mismatch | – |
| 11 | F11 NL edit + undo | Type `swap tuesday dinner for something with chickpeas` → *Apply edit*; then *Undo last edit*; manual *Replace slot* | LLM → structured PlanEdit → Command executed; shopping list refreshes automatically (Observer, refresh count) | `make it nicer` → "No change" with explanation |
| 12 | F12 Assistant | Assistant tab: `What can I cook tonight with food that expires soon?` then `How much of it do I have?` | Tools used are listed; second question relies on memory | *Clear memory*, then ask the follow-up again |

## Video 2 – CLI (`java -jar target/meal-planner.jar --cli`)

Type each line; pause on the output.

```
status
profile
profile budget 80
profile restrictions vegetarian
profile budget -5                                  # F01 error case
pantry
pantry add "frozen peas" 200 g +60 ingredient freezer
pantry add tofu abc g                              # F02 error case
pantry expiring 3
pantry leftovers
search pasta
search-ingredients spinach, egg
search-pantry
recommend --priority pantry
recommend --priority budget
generate-recipe a high-protein vegetarian lunch
save-recipe
plan --priority budget --days 5 --meals breakfast,dinner --note "quick weeknight meals"
shopping
scale r04 8
scale r04 0                                        # F07 error case
substitute r05 milk
leftovers
edit swap wednesday dinner for something with chickpeas
shopping                                           # list changed automatically
history
undo
edit make it nicer                                 # F11 unsupported/ambiguous case
ask what can I cook tonight with food that expires soon?
ask how much of it do I have?
exit
```

## Demonstration index (fill in after uploading)

| Feature | GUI link + timestamp | CLI link + timestamp |
|---------|----------------------|----------------------|
| F01 User preferences and dietary constraints | | |
| F02 Pantry and expiry management | | |
| F03 Ingredient-based recipe search | | |
| F04 Context-aware recommendations | | |
| F05 AI recipe generation | | |
| F06 Weekly meal-plan generation | | |
| F07 Serving-size adjustment | | |
| F08 Ingredient substitution | | |
| F09 Leftover-based planning | | |
| F10 Automatic shopping list | | |
| F11 Natural-language plan modification + undo | | |
| F12 Kitchen assistant (new) | | |
