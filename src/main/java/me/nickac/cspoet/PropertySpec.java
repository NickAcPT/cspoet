/*
 * Copyright (C) 2018 NickAc.
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

import javax.lang.model.SourceVersion;
import java.io.IOException;
import java.util.*;

import static me.nickac.cspoet.Util.checkArgument;

public class PropertySpec {
    public final String name;
    public final CodeBlock javadoc;
    public final List<AttributeSpec> annotations;
    public final Set<CSharpModifier> modifiers;
    public final List<TypeVariableName> typeVariables;
    public final TypeName returnType;
    public final List<ParameterSpec> parameters;
    public final CodeBlock getterCode;
    public final CodeBlock setterCode;


    private PropertySpec(Builder builder) {
        getterCode = builder.getterCode.build();
        setterCode = builder.setterCode.build();

        this.name = Util.checkNotNull(builder.name, "name == null");
        this.javadoc = builder.javadoc.build();
        this.annotations = Util.immutableList(builder.annotations);
        this.modifiers = Util.immutableSet(builder.modifiers);
        this.typeVariables = Util.immutableList(builder.typeVariables);
        this.returnType = builder.returnType;
        this.parameters = Util.immutableList(builder.parameters);
    }

    @SuppressWarnings("Duplicates")
    void emit(CodeWriter codeWriter, String enclosingName, Set<CSharpModifier> implicitModifiers)
            throws IOException {
        codeWriter.emitJavadoc(javadoc);
        codeWriter.emitAnnotations(annotations, false);
        codeWriter.emitModifiers(modifiers, implicitModifiers);

        codeWriter.emit("$T $L($Z", returnType, name);

        boolean firstParameter = true;
        for (Iterator<ParameterSpec> i = parameters.iterator(); i.hasNext(); ) {
            ParameterSpec parameter = i.next();
            if (!firstParameter) codeWriter.emit(",").emitWrappingSpace();
            parameter.emit(codeWriter, false);
            firstParameter = false;
        }

        codeWriter.emit(")");

        if (hasModifier(CSharpModifier.ABSTRACT)) {
            codeWriter.emit(";\n");
        } else {
            codeWriter.emit(" {\n");

            codeWriter.indent();

            if (!getterCode.isEmpty()) {
                codeWriter.emit("get {\n");
                codeWriter.indent();
                codeWriter.emit(getterCode);
                codeWriter.unindent();
                codeWriter.emit("}\n");
            }

            if (!setterCode.isEmpty()) {
                codeWriter.emit("set {\n");
                codeWriter.indent();
                codeWriter.emit(setterCode);
                codeWriter.unindent();
                codeWriter.emit("}\n");
            }

            codeWriter.unindent();

            codeWriter.emit("}\n");
        }
    }

    public boolean hasModifier(CSharpModifier modifier) {
        return modifiers.contains(modifier);
    }

    @Override
    public String toString() {
        StringBuilder out = new StringBuilder();
        try {
            CodeWriter codeWriter = new CodeWriter(out);
            emit(codeWriter, "Constructor", Collections.emptySet());
            return out.toString();
        } catch (IOException e) {
            throw new AssertionError();
        }
    }

    public static final class Builder {
        private final String name;

        private final CodeBlock.Builder javadoc = CodeBlock.builder();
        private final List<AttributeSpec> annotations = new ArrayList<>();
        private final List<CSharpModifier> modifiers = new ArrayList<>();
        private final List<ParameterSpec> parameters = new ArrayList<>();
        private final CodeBlock.Builder getterCode = CodeBlock.builder();
        private final CodeBlock.Builder setterCode = CodeBlock.builder();
        private List<TypeVariableName> typeVariables = new ArrayList<>();
        private TypeName returnType;

        private Builder(String name) {
            Util.checkNotNull(name, "name == null");
            checkArgument(SourceVersion.isName(name),
                    "not a valid name: %s", name);
            this.name = name;
            this.returnType = TypeName.VOID;
        }
    }
}
