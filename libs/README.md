# Local compile-only dependencies

These jars are **not redistributed here** — they belong to their respective authors.
Drop them in this folder before building; they are `compileOnly`, so the produced mod
jar never contains any of their code, and every integration is guarded at runtime by
`ModList.isLoaded(...)`.

| Expected file name | Where to get it | Used for |
|---|---|---|
| `emi-1.1.24-forge.jar` | [EMI](https://www.curseforge.com/minecraft/mc-mods/emi) (Forge 1.20.1) | hiding restricted items from the EMI index |
| `curios-api.jar` | [Curios API](https://www.curseforge.com/minecraft/mc-mods/curios) (Forge 1.20.1) | sweeping Curios slots during enforcement |

JEI is resolved from Maven by Gradle — nothing to download by hand.

Without these files the build fails at compile time. That is intentional: silently
dropping an integration would ship a jar that quietly does less than it claims.
