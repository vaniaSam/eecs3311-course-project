# AI Recipe & Meal Planning Agent (EECS 3311 – Stage 2)

A Java desktop application that helps students, busy cooks and budget-conscious households decide
what to eat. It manages a dietary profile and a pantry with expiry dates, searches a recipe
collection, and uses an **LLM-backed planning agent** to recommend recipes, generate new recipes,
build validated weekly meal plans, suggest substitutions, plan around leftovers, apply plain-English
plan edits (with undo) and answer questions through a tool-using assistant. Everything is available
from both a **Swing GUI** and a **command-line interface (CLI)**.

* Stage 2 report: [`stage2-report.pdf`](stage2-report.pdf) (Word version: `stage2-report.docx`)
* UML diagrams: [`docs/uml/`](docs/uml) · GUI screenshots: [`docs/screenshots/`](docs/screenshots)

## Features

| ID | Feature | AI involvement |
|----|---------|----------------|
| F01 | User preferences, dislikes, dietary restrictions, weekly budget | Deterministic |
| F02 | Pantry and expiry management (leftovers, expiring soon, expired) | Deterministic |
| F03 | Recipe search by keywords, typed ingredients or the whole pantry | Deterministic |
| F04 | Context-aware recommendations (Budget-first / Pantry-first strategy + LLM re-ranking and reasons) | Hybrid |
| F05 | AI recipe generation with restriction validation and automatic revision | AI (validated) |
| F06 | Weekly meal-plan generation: strategy ranking → LLM proposal → validation → revision loop | Hybrid |
| F07 | Recipe serving-size scaling | Deterministic |
| F08 | Ingredient substitutions (curated knowledge retrieval + LLM, checked against restrictions) | Hybrid |
| F09 | Leftover-based planning (choose a catalogue recipe or generate a new one) | Hybrid |
| F10 | Automatic shopping list, kept in sync with plan changes (Observer) | Deterministic |
| F11 | Natural-language meal-plan modification with undo (LLM → PlanEdit → Command) | Hybrid |
| F12 | **New:** kitchen assistant that reasons with read-only tools and remembers the conversation | AI |

Design patterns: **MVC, Facade, Strategy, Command, Observer** (from Stage 1) plus **Adapter**
(LLM client), **Builder** (Recipe) and **Simple Factory** (LLM client creation).

## Prerequisites

* Java **17 or newer** (tested with OpenJDK 21)
* Apache Maven 3.8+ (downloads Gson and JUnit automatically)
* An API key for any OpenAI-compatible chat API **for the AI features** (cheap/free options below).
  Without a key the app still runs: deterministic features work fully, hybrid features fall back to
  their deterministic path and say so, and AI-only features (F05, F12) explain that no LLM is configured.

## Local configuration (API key protection)

Real keys are **never** committed. `.env` and `config.local.json` are listed in `.gitignore`.

```bash
cp .env.example .env
```

Then edit `.env` and enter your own values, for example:

```
LLM_API_KEY=sk-...your key...
LLM_BASE_URL=https://api.openai.com/v1
LLM_MODEL=gpt-4o-mini
```

| Provider | `LLM_BASE_URL` | Example `LLM_MODEL` |
|----------|----------------|---------------------|
| OpenAI (Stage 1 choice) | `https://api.openai.com/v1` | `gpt-4o-mini` |
| Groq (free tier) | `https://api.groq.com/openai/v1` | `openai/gpt-oss-20b` |
| Google Gemini (free tier) | `https://generativelanguage.googleapis.com/v1beta/openai` | `gemini-2.0-flash` |
| OpenRouter | `https://openrouter.ai/api/v1` | `meta-llama/llama-3.1-8b-instruct:free` |
| Ollama (local, free) | `http://localhost:11434/v1` | `llama3.2` (key can be any text) |

Environment variables with the same names override `.env`. The status bar (GUI) and the `status`
command (CLI) show the model in use but never the key.

## Build, test and run

```bash
mvn package
```

This compiles, runs the test suite and produces `target/meal-planner.jar`.

```bash
java -jar target/meal-planner.jar
```

Starts the **GUI** (on a machine without a display it falls back to the CLI).

```bash
java -jar target/meal-planner.jar --cli
```

Starts the **CLI**. Alternatively `mvn exec:java -Dexec.args="--cli"`.

```bash
mvn test
```

Runs the 95 headless tests. The GUI smoke test (drives every tab and saves screenshots) needs a display:

```bash
xvfb-run mvn test -Dheadless=false -Dtest=GuiSmokeTest -Dscreenshots=docs/screenshots
```

## Example CLI session

```
meal-planner> profile restrictions vegetarian
meal-planner> pantry add "frozen peas" 200 g +60 ingredient freezer
meal-planner> pantry expiring 3
meal-planner> search-ingredients spinach, egg
meal-planner> recommend --priority pantry
meal-planner> plan --priority budget --days 5 --meals breakfast,dinner --note "quick weeknight meals"
meal-planner> shopping
meal-planner> edit swap wednesday dinner for something with chickpeas
meal-planner> undo
meal-planner> scale r04 8
meal-planner> substitute r05 milk
meal-planner> leftovers
meal-planner> generate-recipe a high-protein vegetarian lunch
meal-planner> save-recipe
meal-planner> ask what can I cook tonight with food that expires soon?
meal-planner> exit
```

Type `help` for all commands. Multi-word values use double quotes. Expiry dates accept
`YYYY-MM-DD`, `+N` (N days from today) or `-` (unknown). `trace off` hides the agent's step log.
A script can also be piped in: `java -jar target/meal-planner.jar --cli < script.txt`.

## Standalone CLI: running a task from a file

The agent can run a whole task without the GUI. A task file is a list of CLI commands, one per line;
lines starting with `#` are comments. The commands run in order, each one is shown with its output,
and the program exits when the file ends.

### Supported options

Options can be written with one or two dashes, in any letter case (`-input` = `--input`, `-LLMAPI` = `--llmapi`).

| Option | Meaning |
|--------|---------|
| *(none)* | Start the GUI (falls back to the CLI on a machine without a display) |
| `--cli` | Start the interactive command-line interface |
| `--input <file>` | Run the CLI commands in `<file>`, then exit |
| `--llm`, `--llmapi` | Require the LLM API configured in `.env`; stop with an error (exit code 3) if no key is set |
| `--no-llm` | Run without the LLM; hybrid features use their deterministic path, AI-only features say an LLM is needed |
| `--echo` | Print each command before its output (useful with piped input) |
| `--help` | Show the options |

Standard input also works: `java -jar target/meal-planner.jar --cli < examples/task.txt`.

### Working example

[`examples/task.txt`](examples/task.txt) asks the agent to plan three vegetarian dinners that use up food
expiring soon, then produce the shopping list and ask the assistant which meal to cook first:

```
status
profile restrictions vegetarian
pantry expiring 3
recommend --priority pantry --note "use up food that expires soon"
plan --priority pantry --days 3 --meals dinner --note "use up food that expires soon"
shopping
ask Which planned meal should I cook first so nothing goes to waste?
exit
```

Run it with the LLM (requires `LLM_API_KEY` in `.env`):

```bash
java -jar target/meal-planner.jar -LLMAPI -input examples/task.txt
```

Or without an LLM:

```bash
java -jar target/meal-planner.jar --no-llm --input examples/task.txt
```

Expected output (shortened; with the LLM the plan says "assembled by the AI agent"):

```
AI Recipe & Meal Planning Agent - command line
AI enabled: openai/gpt-oss-20b via api.groq.com

meal-planner> profile restrictions vegetarian
Profile saved.
...
meal-planner> plan --priority pantry --days 3 --meals dinner --note "use up food that expires soon"
Plan ACCEPTED (assembled by the AI agent)
...
meal-planner> shopping
Shopping list:
[ ] ...
meal-planner> ask Which planned meal should I cook first so nothing goes to waste?
...
(tools used: ...)

meal-planner> exit
Goodbye.
```

Write your own task file with any commands from `help` and pass it with `--input`.

## GUI overview

Tabs: **F01 Profile**, **F02 Pantry** (table with expired / expiring-soon status), **F03/F07/F08 Recipes**
(search, scale, substitutes), **F04–F06/F09 AI Planner** (request options, recommend, generate
recipe + save, generate plan, leftovers), **F06/F10/F11 Meal Plan** (current plan, live shopping list,
plain-English edit, undo, manual replace) and **F12 Assistant** (chat). LLM calls run in the
background; every AI result shows the agent's steps.

## Project structure

```
src/main/java/mealplanner/
  App.java                 entry point and command-line options (GUI, --cli, --input, --llm)
  config/AppConfig         .env + environment configuration
  model/                   Recipe(+Builder), Pantry, PantryItem, UserProfile, MealPlan (Observer subject), ...
  service/                 RecipeService, PantryService, ShoppingListService, ShoppingListSynchronizer (observer), catalogue loader
  agent/                   MealPlanningAgent, strategies, PlanValidator, DietaryRules, LLM adapter/factory, KitchenAssistant + tools
  command/                 MealPlanCommand, Replace/RemoveMealCommand, CommandExecutor
  controller/              MealPlannerController (MVC), MealPlanningFacade (Facade)
  ui/                      MealPlannerGUI, MealPlannerCLI, ResultFormatter
src/main/resources/data/recipes.json   22 seeded recipes (safe sample data)
examples/task.txt                      example task file for --input
src/test/java/mealplanner/             JUnit 5 tests (scripted LLM test double, local HTTP stub)
```

## Limitations

* Costs are approximate catalogue estimates, not live prices; units are never converted.
* Dietary checks are keyword-based on ingredient names and are **not** an allergy-safety guarantee.
* Data lives in memory for the session (no database), as scoped in Stage 1.
* Quality of AI output depends on the configured model; all AI output is validated before it is accepted.
