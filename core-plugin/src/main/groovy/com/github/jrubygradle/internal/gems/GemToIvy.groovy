package com.github.jrubygradle.internal.gems

import com.github.jrubygradle.api.gems.GemInfo
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.xml.MarkupBuilder

import java.security.MessageDigest

import static com.github.jrubygradle.api.gems.GemVersion.singleGemVersionFromMultipleGemRequirements

/** Converts from Gem metadata to Ivy metadata.
 *
 * @author Schalk W. Cronjé
 *
 * @since 2.0
 */
@CompileStatic
class GemToIvy {

    /** Create a converter from GEM metadata to Ivy metadata.
     *
     * This constructor version assumes that the group is called {@code rubygems}.
     * @param serverUri URI of the RubyGems server.
     */
    GemToIvy(URI serverUri) {
        this.serverUri = serverUri.toString()
    }

    /** Create a converter from GEM metadata to Ivy metadata.
     *
     * @param serverUri URI of the RubyGems server.
     * @param group Use a group name other than {@code rubygems}.
     */
    GemToIvy(URI serverUri, String group) {
        this.serverUri = serverUri.toString()
        this.org = group
    }

    /** Write the Ivy metadata.
     *
     * @param writer Writer for output
     * @param gem GEM metadata
     * @return {@code writer} after population with Ivy metadata in XML format.
     */
    @CompileDynamic
    @SuppressWarnings('NoDef')
    Writer writeTo(Writer writer, GemInfo gem) {
        def xml = new MarkupBuilder(writer)

        final Map artifactAttributes = [
            type: 'gem', url: gem.gemUri
        ]

        if (gem.platform == JAVA_PLATFORM) {
            artifactAttributes['e:classifier'] = JAVA_PLATFORM
        }

        xml.'ivy-module'(
            'xmlns:xsi': 'http://www.w3.org/2001/XMLSchema-instance',
            'xsi:noNamespaceSchemaLocation': 'http://ant.apache.org/ivy/schemas/ivy.xsd',
            'xmlns:e': 'http://ant.apache.org/ivy/extra',
            version: '2.0'
        ) {
            info(organisation: this.org, module: gem.name, revision: gem.version /*, publication: */) {
                if (gem.description || gem.homepageUri) {
                    if (gem.homepageUri) {
                        description(homepage: gem.homepageUri) {
                            gem.description ?: ''
                        }
                    } else {
                        description {
                            gem.description ?: ''
                        }
                    }
                }
                /* <license name='' url=''> 1..n */
            }

            publications {
                artifact(artifactAttributes)
            }

            if (gem.dependencies || gem.jarRequirements) {
                dependencies {
                    gem.dependencies.each { dep ->
                        dependency(
                            org: this.org,
                            name: dep.name,
                            rev: singleGemVersionFromMultipleGemRequirements(dep.requirements).toString()
                        )
                    }
                    gem.jarRequirements.each { dep ->
                        if (dep.group) {
                            dependency(
                                org: dep.group,
                                name: dep.name,
                                rev: singleGemVersionFromMultipleGemRequirements(dep.requirements).toString()
                            )
                        } else {
                            dependency(
                                name: dep.name,
                                rev: singleGemVersionFromMultipleGemRequirements(dep.requirements).toString()
                            )
                        }
                    }
                }
            }
        }

        writer
    }

    /** Write the Ivy metadata to a string.
     *
     * @param gem GEM metadata.
     * @return String containing the Ivy metadata in XML format.
     */
    String write(GemInfo gem) {
        StringWriter writer = new StringWriter()
        writeTo(writer, gem)
        writer.toString()
    }

    /** Writes the SHA1 checksum of the {@code ivy.xmnl} file.
     *
     * @param ivyXml Fle containing the {@code ivy.xml} content/
     * @return Checksum file.
     */
    File writeSha1(File ivyXml) {
        File shaFile = new File(ivyXml.parentFile, "${ivyXml.name}.sha1")
        shaFile.text = MessageDigest.getInstance('SHA-1').digest(ivyXml.bytes).encodeHex().toString()
        shaFile
    }

    private static final String JAVA_PLATFORM = 'java'
    private final String serverUri
    private final String org = 'rubygems'
}
