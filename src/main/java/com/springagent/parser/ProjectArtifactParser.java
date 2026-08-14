package com.springagent.parser;

import java.io.InputStream;

public interface ProjectArtifactParser<T> {

    ArtifactType supportedType();

    T parse(InputStream input);
}
