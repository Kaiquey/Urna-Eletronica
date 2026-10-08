plugins {
    java
    id("edu.sc.seis.launch4j") version "3.0.5"
}

launch4j {
    mainClassName = "view.UrnaView"
    outfile = "Urna-Eletronica.exe"
    headerType = "gui"
}

sourceSets {
    main {
        java {
            setSrcDirs(listOf("Urna/src")) /
        }
    }
}