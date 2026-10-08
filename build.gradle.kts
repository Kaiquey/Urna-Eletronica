plugins {
    java
    id("edu.sc.seis.launch4j") version "3.0.5"
}

launch4j {
    mainClassName = "com.exemplo.service.UrnaView"
    jar.set(tasks.jar.flatMap { it.archiveFile }.map { it.asFile.absolutePath })
    outfile = "Urna.exe"
    dontWrapJar = false
    headerType = "gui" 
}
