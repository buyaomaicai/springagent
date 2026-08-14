package com.springagent.parser.bom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalMavenSpringBootBomResolverTests {

    @TempDir
    Path localRepository;

    @Test
    void readsDirectManagedVersionsAndProperties() throws IOException {
        writeBom("""
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-dependencies</artifactId>
                    <version>3.2.5</version>
                    <packaging>pom</packaging>
                    <properties>
                        <tomcat.version>10.1.20</tomcat.version>
                    </properties>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>org.springframework.boot</groupId>
                                <artifactId>spring-boot-starter-web</artifactId>
                                <version>3.2.5</version>
                            </dependency>
                            <dependency>
                                <groupId>org.apache.tomcat.embed</groupId>
                                <artifactId>tomcat-embed-core</artifactId>
                                <version>${tomcat.version}</version>
                            </dependency>
                            <dependency>
                                <groupId>com.fasterxml.jackson</groupId>
                                <artifactId>jackson-bom</artifactId>
                                <version>2.15.4</version>
                                <type>pom</type>
                                <scope>import</scope>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                </project>
                """);

        ResolvedSpringBootBom result = resolver().resolve("3.2.5");

        assertTrue(result.available());
        assertEquals(
                "3.2.5",
                result.findVersion(key(
                        "org.springframework.boot",
                        "spring-boot-starter-web"
                )).orElseThrow()
        );
        assertEquals(
                "10.1.20",
                result.findVersion(key(
                        "org.apache.tomcat.embed",
                        "tomcat-embed-core"
                )).orElseThrow()
        );
        assertTrue(result.findVersion(new MavenDependencyKey(
                "com.fasterxml.jackson",
                "jackson-bom",
                "pom",
                null
        )).isEmpty());
        assertTrue(result.warnings().stream().anyMatch(
                warning -> warning.contains("嵌套 BOM")
        ));
    }

    @Test
    void reportsMissingLocalBomWithoutDownloading() {
        ResolvedSpringBootBom result = resolver().resolve("3.2.5");

        assertFalse(result.available());
        assertTrue(result.managedVersions().isEmpty());
        assertTrue(result.warnings().get(0).contains("不存在"));
    }

    @Test
    void rejectsUnsafeVersionBeforeResolvingPath() {
        ResolvedSpringBootBom result = resolver().resolve("../../etc");

        assertFalse(result.available());
        assertTrue(result.warnings().get(0).contains("格式不安全"));
    }

    private LocalMavenSpringBootBomResolver resolver() {
        return new LocalMavenSpringBootBomResolver(localRepository);
    }

    private MavenDependencyKey key(
            String groupId,
            String artifactId
    ) {
        return new MavenDependencyKey(
                groupId,
                artifactId,
                null,
                null
        );
    }

    private void writeBom(String xml) throws IOException {
        Path directory = localRepository.resolve(Path.of(
                "org",
                "springframework",
                "boot",
                "spring-boot-dependencies",
                "3.2.5"
        ));
        Files.createDirectories(directory);
        Files.writeString(
                directory.resolve(
                        "spring-boot-dependencies-3.2.5.pom"
                ),
                xml
        );
    }
}
