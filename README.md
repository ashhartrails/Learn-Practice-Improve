# Learn Anything (working name)

A personal Android tutor. You type a goal, and AI builds a course (Subject > Chapter > Topic)
with short interactive lessons, quizzes and practicals that the app checks itself.
See [CLAUDE.md](CLAUDE.md) for the product and architecture rules, and
[docs/SETUP.md](docs/SETUP.md) to build and run it on your phone.

**Status:** Milestone 0 (Foundations). You can create a course outline with Gemini; it is
validated against a JSON schema and saved on the phone.

```
app/            Activity, navigation (Navigation 3), bottom bar
core/model      domain types, JSON schemas, versioned prompts, outline rules
core/ai         AiProvider, Gemini provider, router, schema validation, StructuredCaller
core/data       Room 3 database, repositories, encrypted settings, use cases
core/ui         Material 3 theme (dynamic colour, true black dark mode), shared components
feature/*       home, create, subject, settings screens
build-logic/    Gradle convention plugins
```
