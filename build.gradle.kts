plugins { java }

group = "fr.toolrepair"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")
    // Vault (économie)
    compileOnly("com.github.MilkBowl:VaultAPI:1.7")
    // Ton plugin CustomTools (version PATCHÉE, voir README) : copie son jar dans le dossier libs/
    compileOnly(files("libs/CustomTools.jar"))
}

java { toolchain.languageVersion.set(JavaLanguageVersion.of(25)) }

tasks.jar { archiveFileName.set("ToolRepair.jar") }
