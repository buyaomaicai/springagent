package com.springagent.parser.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.springagent.diagnosis.model.ProjectInput;
import com.springagent.diagnosis.model.VersionSource;
import com.springagent.parser.ArtifactType;
import com.springagent.parser.bom.MavenDependencyKey;
import com.springagent.parser.bom.ResolvedSpringBootBom;
import com.springagent.parser.exception.ProjectArtifactParseException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PomXmlParserTests {

    private static final int MAX_CONTENT_SIZE = 1024 * 1024;

    private final PomXmlParser parser = new PomXmlParser(
            version -> new ResolvedSpringBootBom(
                    version,
                    true,
                    Map.of(),
                    List.of()
            )
    );

    @Test
    void parsesBasicSpringBootPom() {
        ProjectInput result = parse("""
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>2.7.18</version>
                    </parent>
                    <groupId>com.example</groupId>
                    <artifactId>demo</artifactId>
                    <version>1.0.0</version>
                    <properties>
                        <java.version>17</java.version>
                    </properties>
                    <modules>
                        <module>demo-api</module>
                        <module>demo-service</module>
                    </modules>
                </project>
                """);

        assertEquals(ArtifactType.POM_XML, parser.supportedType());
        assertEquals("com.example", result.groupId());
        assertEquals("demo", result.artifactId());
        assertEquals("1.0.0", result.version());
        assertEquals("jar", result.packaging());
        assertEquals("17", result.javaVersion());
        assertEquals("2.7.18", result.springBootVersion());
        assertEquals(
                List.of("demo-api", "demo-service"),
                result.modules()
        );
        assertTrue(result.dependencies().isEmpty());
        assertTrue(result.plugins().isEmpty());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void inheritsGroupAndVersionFromParent() {
        ProjectInput result = parse("""
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>com.example</groupId>
                        <artifactId>example-parent</artifactId>
                        <version>3.2.1</version>
                    </parent>
                    <artifactId>child-module</artifactId>
                </project>
                """);

        assertEquals("com.example", result.groupId());
        assertEquals("child-module", result.artifactId());
        assertEquals("3.2.1", result.version());
        assertEquals("jar", result.packaging());
        assertNull(result.springBootVersion());
    }

    @Test
    void reportsProfilesThatWereNotApplied() {
        ProjectInput result = parse("""
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>profile-demo</artifactId>
                    <version>1.0.0</version>
                    <profiles>
                        <profile>
                            <id>production</id>
                        </profile>
                    </profiles>
                </project>
                """);

        assertEquals(1, result.warnings().size());
        assertTrue(result.warnings().get(0).contains("Profile"));
    }

    @Test
    void detectsSpringBootVersionFromImportedBomProperty() {
        ProjectInput result = parse("""
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>bom-demo</artifactId>
                    <version>1.0.0</version>
                    <properties>
                        <spring-boot.version>3.2.5</spring-boot.version>
                    </properties>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>org.springframework.boot</groupId>
                                <artifactId>spring-boot-dependencies</artifactId>
                                <version>${spring-boot.version}</version>
                                <type>pom</type>
                                <scope>import</scope>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-web</artifactId>
                        </dependency>
                    </dependencies>
                </project>
                """);

        assertEquals("3.2.5", result.springBootVersion());
        assertEquals(1, result.dependencies().size());
        assertNull(result.dependencies().get(0).version());
        assertEquals(
                VersionSource.UNRESOLVED,
                result.dependencies().get(0).versionSource()
        );
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void resolvesMissingDependencyVersionFromSpringBootBom() {
        MavenDependencyKey starterWeb = new MavenDependencyKey(
                "org.springframework.boot",
                "spring-boot-starter-web",
                "jar",
                null
        );
        PomXmlParser parserWithBom = new PomXmlParser(
                version -> new ResolvedSpringBootBom(
                        version,
                        true,
                        Map.of(starterWeb, version),
                        List.of()
                )
        );

        ProjectInput result = parserWithBom.parse(inputOf("""
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>3.2.5</version>
                    </parent>
                    <artifactId>bom-managed-dependency</artifactId>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-web</artifactId>
                        </dependency>
                    </dependencies>
                </project>
                """));

        assertEquals(
                "3.2.5",
                result.dependencies().get(0).version()
        );
        assertEquals(
                VersionSource.SPRING_BOOT_BOM,
                result.dependencies().get(0).versionSource()
        );
    }

    @Test
    void prefersImportedBomWhenBootParentVersionDiffers() {
        ProjectInput result = parse("""
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>2.7.18</version>
                    </parent>
                    <artifactId>mixed-boot-versions</artifactId>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>org.springframework.boot</groupId>
                                <artifactId>spring-boot-dependencies</artifactId>
                                <version>3.2.5</version>
                                <type>pom</type>
                                <scope>import</scope>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                </project>
                """);

        assertEquals("3.2.5", result.springBootVersion());
        assertTrue(result.warnings().stream().anyMatch(
                warning -> warning.contains("不一致")
        ));
    }

    @Test
    void ignoresSpringBootDependenciesWithoutBomImportSemantics() {
        ProjectInput result = parse("""
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>not-a-boot-bom</artifactId>
                    <version>1.0.0</version>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>org.springframework.boot</groupId>
                                <artifactId>spring-boot-dependencies</artifactId>
                                <version>3.2.5</version>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                </project>
                """);

        assertNull(result.springBootVersion());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void rejectsNullAndEmptyContent() {
        assertParseFailure(
                null,
                ProjectArtifactParseException.Reason.EMPTY_CONTENT
        );
        assertParseFailure(
                new ByteArrayInputStream(new byte[0]),
                ProjectArtifactParseException.Reason.EMPTY_CONTENT
        );
    }

    @Test
    void rejectsContentLargerThanLimit() {
        byte[] content = new byte[MAX_CONTENT_SIZE + 1];

        assertParseFailure(
                new ByteArrayInputStream(content),
                ProjectArtifactParseException.Reason.CONTENT_TOO_LARGE
        );
    }

    @Test
    void rejectsMalformedXml() {
        assertParseFailure(
                inputOf("""
                        <project>
                            <modelVersion>4.0.0</modelVersion>
                            <artifactId>broken
                        </project>
                        """),
                ProjectArtifactParseException.Reason.MALFORMED_CONTENT
        );
    }

    @Test
    void rejectsDoctypeAndExternalEntity() {
        assertParseFailure(
                inputOf("""
                        <?xml version="1.0" encoding="UTF-8"?>
                        <!DOCTYPE project [
                            <!ENTITY secret SYSTEM "file:///etc/passwd">
                        ]>
                        <project>
                            <modelVersion>4.0.0</modelVersion>
                            <artifactId>&secret;</artifactId>
                        </project>
                        """),
                ProjectArtifactParseException.Reason.UNSAFE_CONTENT
        );
    }

    @Test
    void rejectsMissingArtifactId() {
        assertParseFailure(
                inputOf("""
                        <project xmlns="http://maven.apache.org/POM/4.0.0">
                            <modelVersion>4.0.0</modelVersion>
                            <groupId>com.example</groupId>
                            <version>1.0.0</version>
                        </project>
                        """),
                ProjectArtifactParseException.Reason.MISSING_REQUIRED_FIELD
        );
    }

    @Test
    void rejectsUnsupportedModelVersion() {
        assertParseFailure(
                inputOf("""
                        <project>
                            <modelVersion>5.0.0</modelVersion>
                            <artifactId>future-project</artifactId>
                        </project>
                        """),
                ProjectArtifactParseException.Reason.UNSUPPORTED_FORMAT
        );
    }

    private ProjectInput parse(String xml) {
        return parser.parse(inputOf(xml));
    }

    private InputStream inputOf(String content) {
        return new ByteArrayInputStream(
                content.getBytes(StandardCharsets.UTF_8)
        );
    }

    private void assertParseFailure(
            InputStream input,
            ProjectArtifactParseException.Reason expectedReason
    ) {
        ProjectArtifactParseException exception = assertThrows(
                ProjectArtifactParseException.class,
                () -> parser.parse(input)
        );

        assertEquals(ArtifactType.POM_XML, exception.getArtifactType());
        assertEquals(expectedReason, exception.getReason());
    }
}
