/*
 * Copyright (C) 2015 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package me.nickac.cspoet;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import javax.tools.SimpleJavaFileObject;
import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static java.nio.charset.StandardCharsets.UTF_8;
import static me.nickac.cspoet.Util.checkArgument;

/**
 * A CSharp file containing a single top level class.
 */
public final class CSharpFile {
    private static final Appendable NULL_APPENDABLE = new Appendable() {
        @Override
        public Appendable append(CharSequence charSequence) {
            return this;
        }

        @Override
        public Appendable append(CharSequence charSequence, int start, int end) {
            return this;
        }

        @Override
        public Appendable append(char c) {
            return this;
        }
    };

    public final CodeBlock fileComment;
    public final String namespace;
    public final TypeSpec typeSpec;
    public final boolean skipJavaLangImports;
    private final Set<String> staticImports;
    private final Set<String> nonStaticImports;
    private final String indent;

    private CSharpFile(Builder builder) {
        this.fileComment = builder.fileComment.build();
        this.namespace = builder.namespace;
        this.typeSpec = builder.typeSpec;
        this.skipJavaLangImports = builder.skipJavaLangImports;
        this.staticImports = Util.immutableSet(builder.staticImports);
        this.nonStaticImports = Util.immutableSet(builder.nonStaticImports);
        this.indent = builder.indent;
    }

    public static Builder builder(String namespace, TypeSpec typeSpec) {
        Util.checkNotNull(namespace, "namespace == null");
        Util.checkNotNull(typeSpec, "typeSpec == null");
        return new Builder(namespace, typeSpec);
    }

    public void writeTo(Appendable out) throws IOException {
        // First pass: emit the entire class, just to collect the types we'll need to import.
        CodeWriter importsCollector = new CodeWriter(NULL_APPENDABLE, indent, staticImports, nonStaticImports);
        emit(importsCollector);
        Map<String, ClassName> suggestedImports = importsCollector.suggestedImports();

        // Second pass: write the code, taking advantage of the imports.
        CodeWriter codeWriter = new CodeWriter(out, indent, suggestedImports, staticImports, nonStaticImports);
        emit(codeWriter);
    }

    /**
     * Writes this to {@code directory} as UTF-8 using the standard directory structure.
     */
    public void writeTo(Path directory) throws IOException {
        checkArgument(Files.notExists(directory) || Files.isDirectory(directory),
                "path %s exists but is not a directory.", directory);
        Path outputDirectory = directory;
        if (!namespace.isEmpty()) {
            for (String packageComponent: namespace.split("\\.")) {
                outputDirectory = outputDirectory.resolve(packageComponent);
            }
            Files.createDirectories(outputDirectory);
        }

        Path outputPath = outputDirectory.resolve(typeSpec.name + ".java");
        try (Writer writer = new OutputStreamWriter(Files.newOutputStream(outputPath), UTF_8)) {
            writeTo(writer);
        }
    }

    /**
     * Writes this to {@code directory} as UTF-8 using the standard directory structure.
     */
    public void writeTo(File directory) throws IOException {
        writeTo(directory.toPath());
    }

    /**
     * Writes this to {@code filer}.
     */
    public void writeTo(Filer filer) throws IOException {
        String fileName = namespace.isEmpty()
                ? typeSpec.name
                : namespace + "." + typeSpec.name;
        List<Element> originatingElements = typeSpec.originatingElements;
        JavaFileObject filerSourceFile = filer.createSourceFile(fileName,
                originatingElements.toArray(new Element[originatingElements.size()]));
        try (Writer writer = filerSourceFile.openWriter()) {
            writeTo(writer);
        } catch (Exception e) {
            try {
                filerSourceFile.delete();
            } catch (Exception ignored) {
            }
            throw e;
        }
    }

    private void emit(CodeWriter codeWriter) throws IOException {
        codeWriter.pushNamespace(namespace);

        if (!fileComment.isEmpty()) {
            codeWriter.emitComment(fileComment);
        }

        int importedTypesCount = 0;
        for (ClassName className: new TreeSet<>(codeWriter.importedTypes().values())) {
            if (skipJavaLangImports && className.packageName().equals("java.lang")) continue;
            codeWriter.emit("using $L;\n", className.packageName());
            importedTypesCount++;
        }

        if (!nonStaticImports.isEmpty()) {
            for (String signature: nonStaticImports) {
                codeWriter.emit("using $L;\n", signature);
                importedTypesCount++;
            }
        }

        if (importedTypesCount > 0) {
            codeWriter.emit("\n");
        }

        if (!staticImports.isEmpty()) {
            for (String signature: staticImports) {
                codeWriter.emit("using static $L;\n", signature);
            }
            codeWriter.emit("\n");
        }

        if (!namespace.isEmpty()) {
            codeWriter.emit("namespace $L {\n", namespace);
            codeWriter.indent();
        }

        typeSpec.emit(codeWriter, null, Collections.emptySet());


        if (!namespace.isEmpty()) {
            codeWriter.unindent();
            codeWriter.emit("}");
        }
        codeWriter.popPackage();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        if (getClass() != o.getClass()) return false;
        return toString().equals(o.toString());
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    @Override
    public String toString() {
        try {
            StringBuilder result = new StringBuilder();
            writeTo(result);
            return result.toString();
        } catch (IOException e) {
            throw new AssertionError();
        }
    }

    public JavaFileObject toJavaFileObject() {
        URI uri = URI.create((namespace.isEmpty()
                ? typeSpec.name
                : namespace.replace('.', '/') + '/' + typeSpec.name)
                + Kind.SOURCE.extension);
        return new SimpleJavaFileObject(uri, Kind.SOURCE) {
            private final long lastModified = System.currentTimeMillis();

            @Override
            public String getCharContent(boolean ignoreEncodingErrors) {
                return CSharpFile.this.toString();
            }

            @Override
            public InputStream openInputStream() throws IOException {
                return new ByteArrayInputStream(getCharContent(true).getBytes(UTF_8));
            }

            @Override
            public long getLastModified() {
                return lastModified;
            }
        };
    }

    public Builder toBuilder() {
        Builder builder = new Builder(namespace, typeSpec);
        builder.fileComment.add(fileComment);
        builder.skipJavaLangImports = skipJavaLangImports;
        builder.indent = indent;
        return builder;
    }

    public static final class Builder {
        private final String namespace;
        private final TypeSpec typeSpec;
        private final CodeBlock.Builder fileComment = CodeBlock.builder();
        private final Set<String> staticImports = new LinkedHashSet<>();
        private final Set<String> nonStaticImports = new LinkedHashSet<>();
        private boolean skipJavaLangImports;
        private String indent = "\t";

        private Builder(String namespace, TypeSpec typeSpec) {
            this.namespace = namespace;
            this.typeSpec = typeSpec;
        }

        public Builder addFileComment(String format, Object... args) {
            this.fileComment.add(format, args);
            return this;
        }

        public Builder addStaticUsing(Enum<?> constant) {
            return addStaticUsing(ClassName.get(constant.getDeclaringClass()), constant.name());
        }

        public Builder addStaticUsing(Class<?> clazz, String... names) {
            return addStaticUsing(ClassName.get(clazz), names);
        }

        public Builder addUsing(String importValue) {
            nonStaticImports.add(importValue);
            return this;
        }


        public Builder addStaticUsing(ClassName className, String... names) {
            checkArgument(className != null, "className == null");
            checkArgument(names != null, "names == null");
            checkArgument(names.length > 0, "names array is empty");
            for (String name: names) {
                checkArgument(name != null, "null entry in names array: %s", Arrays.toString(names));
                staticImports.add(className.canonicalName + "." + name);
            }
            return this;
        }

        /**
         * Call this to omit imports for classes in {@code java.lang}, such as {@code java.lang.String}.
         *
         * <p>By default, JavaPoet explicitly imports types in {@code java.lang} to defend against
         * naming conflicts. Suppose an (ill-advised) class is named {@code com.example.String}. When
         * {@code java.lang} imports are skipped, generated code in {@code com.example} that references
         * {@code java.lang.String} will get {@code com.example.String} instead.
         */
        public Builder skipJavaLangImports(boolean skipJavaLangImports) {
            this.skipJavaLangImports = skipJavaLangImports;
            return this;
        }

        public Builder indent(String indent) {
            this.indent = indent;
            return this;
        }

        public CSharpFile build() {
            return new CSharpFile(this);
        }
    }
}
