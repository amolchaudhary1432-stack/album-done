// Groups the faces of a measured folder into people and writes the planner's input, using the
// app's own Kotlin rules (no copy): the plan/ and people/ sources are compiled straight from app/.
plugins {
    kotlin("jvm") version "2.0.20"
    application
}

repositories { mavenCentral() }

val app = rootDir.resolve("../../../app/src/main/java").canonicalPath

sourceSets {
    main {
        kotlin {
            srcDir("src")
            srcDir(app)
            include(
                "com/albumdone/harvest/**",
                "com/albumdone/app/plan/**",
                "com/albumdone/app/people/PeopleClusterer.kt",
                "com/albumdone/app/people/FamilyGuess.kt",
                "com/albumdone/app/people/PeoplePlan.kt",
            )
        }
    }
}

application { mainClass.set("com.albumdone.harvest.HarvestPlanKt") }
