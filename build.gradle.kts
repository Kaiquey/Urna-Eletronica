plugins {
    java
    id("edu.sc.seis.launch4j") version "3.0.5"
}

launch4j {
    mainClassName = "com.exemplo.service.UrnaView"
    jar = tasks.jar.get().archiveFile.get().asFile.absolutePath
    outfile = "Urna.exe"
    dontWrapJar = false
    headerType = "gui" 
}