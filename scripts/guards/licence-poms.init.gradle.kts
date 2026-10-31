// Init script for scripts/guards/licences.py: asks Gradle for the POM of every
// library the guard checks, and of their parent POMs, and writes down where each one is.
//
// The guard cannot simply look for POMs in the Gradle cache. Once a library is resolved, Gradle
// works from the metadata it parsed and keeps elsewhere, and never opens the POM file again; its
// cache cleanup then deletes the POM as unused. On CI setup-gradle runs that cleanup after every
// run, so from the second run on the POMs were gone. Resolving them here downloads
// the missing ones and marks them as used.
//
//   ./gradlew -q --no-configuration-cache --init-script <this file> licencePoms \
//       -PlicencePomsIn=<one group:artifact:version per line> -PlicencePomsOut=<file>
//
// Each output line is `group:artifact:version<TAB>path of the POM`, or
// `group:artifact:version<TAB>missing: <why>` when Gradle could not get it.

import javax.xml.parsers.DocumentBuilderFactory
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.artifacts.result.UnresolvedArtifactResult
import org.gradle.api.artifacts.result.UnresolvedComponentResult
import org.gradle.maven.MavenModule
import org.gradle.maven.MavenPomArtifact
import org.w3c.dom.Element

fun Element.child(tag: String): Element? =
    (0 until childNodes.length).map(childNodes::item).filterIsInstance<Element>()
        .firstOrNull { it.tagName == tag }

/** `group:artifact:version` of the POM's `<parent>`, or null when it has none. */
fun parentOf(pom: File): String? {
    val parent = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(pom)
        .documentElement.child("parent") ?: return null
    val parts = listOf("groupId", "artifactId", "version").map { parent.child(it)?.textContent?.trim().orEmpty() }
    return if (parts.all(String::isNotEmpty)) parts.joinToString(":") else null
}

gradle.rootProject {
    tasks.register("licencePoms") {
        notCompatibleWithConfigurationCache("resolves POMs through the project's dependencies while it runs")
        val input = providers.gradleProperty("licencePomsIn")
        val output = providers.gradleProperty("licencePomsOut")
        doLast {
            val lines = mutableListOf<String>()
            val asked = mutableSetOf<String>()
            var wanted = file(input.get()).readLines().map(String::trim).filter(String::isNotEmpty).toSet()
            // Parents are only known once their child's POM is read, so this goes round per level.
            while (wanted.isNotEmpty()) {
                asked += wanted
                val query = dependencies.createArtifactResolutionQuery()
                wanted.forEach { coordinate ->
                    val (group, artifact, version) = coordinate.split(":")
                    query.forModule(group, artifact, version)
                }
                val result = query.withArtifacts(MavenModule::class.java, MavenPomArtifact::class.java).execute()
                val parents = mutableSetOf<String>()
                for (component in result.resolvedComponents) {
                    val id = component.id as ModuleComponentIdentifier
                    val coordinate = "${id.group}:${id.module}:${id.version}"
                    for (pom in component.getArtifacts(MavenPomArtifact::class.java)) {
                        when (pom) {
                            is ResolvedArtifactResult -> {
                                lines += "$coordinate\t${pom.file}"
                                parentOf(pom.file)?.let(parents::add)
                            }
                            is UnresolvedArtifactResult -> lines += "$coordinate\tmissing: ${pom.failure.message}"
                        }
                    }
                }
                for (component in result.components.filterIsInstance<UnresolvedComponentResult>()) {
                    lines += "${component.id.displayName}\tmissing: ${component.failure.message}"
                }
                wanted = parents - asked
            }
            file(output.get()).writeText(lines.joinToString("") { it.replace('\n', ' ') + "\n" })
        }
    }
}
