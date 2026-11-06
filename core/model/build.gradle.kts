plugins { alias(libs.plugins.kotlin.jvm) }
kotlin { jvmToolchain(21) }
val domainContractTest by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("pl.chatra.core.model.DomainContractTestKt")
}
tasks.test { dependsOn(domainContractTest) }
