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

    public static Builder propertyBuilder(String name) {
        return new Builder(name);
    }

    @SuppressWarnings("Duplicates")
    void emit(CodeWriter codeWriter, String enclosingName, Set<CSharpModifier> implicitModifiers)
            throws IOException {
        codeWriter.emitJavadoc(javadoc);
        codeWriter.emitAnnotations(annotations, false);
        codeWriter.emitModifiers(modifiers, implicitModifiers);

        codeWriter.emit("$t $L", returnType, name);

        boolean firstParameter = true;
        for (Iterator<ParameterSpec> i = parameters.iterator(); i.hasNext(); ) {
            ParameterSpec parameter = i.next();
            if (!firstParameter) codeWriter.emit(",").emitWrappingSpace();
            parameter.emit(codeWriter, false);
            firstParameter = false;
        }

        if (hasModifier(CSharpModifier.ABSTRACT)) {
            codeWriter.emit(";\n");
        } else if (!getterCode.isEmpty() && getterCode.statementCount == 1 && setterCode.isEmpty()) {
            codeWriter.emit(" => ");
            String getter = getterCode.toString();
            if (getter.startsWith("return "))
                getter = getter.substring(7);
            codeWriter.emit(getter);
        } else {
            codeWriter.emit(" {\n");

            codeWriter.indent();

            boolean hasInsertedNewLine = false;

            if (!getterCode.isEmpty()) {
                if (getterCode.toString().equals(";")) {
                    codeWriter.emit("get; ");
                } else if (getterCode.statementCount != 1) {
                    codeWriter.emit("get {" + ((getterCode.statementCount > 1) ? "\n" : " "));
                    codeWriter.indent();
                    codeWriter.emit(getterCode);
                    codeWriter.unindent();
                    codeWriter.emit("}\n");
                    hasInsertedNewLine = true;
                } else {
                    codeWriter.emit("get => ");
                    String getter = getterCode.toString();
                    if (getter.startsWith("return "))
                        getter = getter.substring(7);
                    codeWriter.emit(getter);
                }
            }

            if (!setterCode.isEmpty()) {
                if (setterCode.toString().equals(";")) {
                    codeWriter.emit("set; ");
                } else if (setterCode.statementCount != 1) {
                    codeWriter.emit("set {\n");
                    codeWriter.indent();
                    codeWriter.emit(setterCode);
                    codeWriter.unindent();
                    codeWriter.emit("}\n");
                    hasInsertedNewLine = true;
                } else {
                    codeWriter.emit("set => ");
                    codeWriter.emit(setterCode);
                }
            }

            if (!hasInsertedNewLine)
                codeWriter.emit("\n");

            codeWriter.unindent();

            codeWriter.emit("}\n");
        }
    }

    public boolean hasModifier(CSharpModifier modifier) {
        return modifiers.contains(modifier);
    }


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

    public String[] getUsings() {
        CodeWriter importsCollector = new CodeWriter(NULL_APPENDABLE, "", Collections.emptySet(), Collections.emptySet());
        try {
            emit(importsCollector, "", Collections.singleton(CSharpModifier.PRIVATE));
        } catch (IOException e) {
            return new String[0];
        }
        Map<String, ClassName> suggestedImports = importsCollector.suggestedImports();
        return suggestedImports.entrySet().stream().map(c -> String.format("%s", c.getValue().packageName())).toArray(String[]::new);
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
        private CodeBlock.Builder getterCode = CodeBlock.builder();
        private CodeBlock.Builder setterCode = CodeBlock.builder();
        private List<TypeVariableName> typeVariables = new ArrayList<>();
        private TypeName returnType;

        private Builder(String name) {
            Util.checkNotNull(name, "name == null");
            //checkArgument(SourceVersion.isName(name), "not a valid name: %s", name);
            this.name = name;
            this.returnType = TypeName.VOID;
        }

        public Builder addModifier(CSharpModifier modifier) {
            modifiers.add(modifier);
            return this;
        }

        public Builder returns(TypeName returnType) {
            this.returnType = returnType;
            return this;
        }

        public PropertySpec build() {
            return new PropertySpec(this);
        }

        public GetterBuilder getter() {
            return new GetterBuilder(this);
        }

        public SetterBuilder setter() {
            return new SetterBuilder(this);
        }

        public class GetterBuilder {
            private final CodeBlock.Builder code = CodeBlock.builder();
            private final PropertySpec.Builder parent;

            public GetterBuilder(Builder parent) {
                this.parent = parent;
            }

            public PropertySpec.Builder empty() {
                parent.getterCode = CodeBlock.of(";").toBuilder();
                return parent;
            }

            /**
             * @param controlFlow the control flow construct and its code, such as "if (foo == 5)".
             *                    Shouldn't contain braces or newline characters.
             */
            public GetterBuilder beginControlFlow(String controlFlow, Object... args) {
                code.beginControlFlow(controlFlow, args);
                return this;
            }

            /**
             * @param controlFlow the control flow construct and its code, such as "else if (foo == 10)".
             *                    Shouldn't contain braces or newline characters.
             */
            public GetterBuilder nextControlFlow(String controlFlow, Object... args) {
                code.nextControlFlow(controlFlow, args);
                return this;
            }

            public GetterBuilder endControlFlow() {
                code.endControlFlow();
                return this;
            }

            /**
             * @param controlFlow the optional control flow construct and its code, such as
             *                    "while(foo == 20)". Only used for "do/while" control flows.
             */
            public GetterBuilder endControlFlow(String controlFlow, Object... args) {
                code.endControlFlow(controlFlow, args);
                return this;
            }

            public GetterBuilder addStatement(String format, Object... args) {
                code.addStatement(format, args);
                return this;
            }

            public GetterBuilder addStatement(CodeBlock codeBlock) {
                code.addStatement(codeBlock);
                return this;
            }

            public PropertySpec.Builder endGetter() {
                parent.getterCode = code;
                return parent;
            }
        }

        public class SetterBuilder {
            private final CodeBlock.Builder code = CodeBlock.builder();
            private final PropertySpec.Builder parent;

            public SetterBuilder(Builder parent) {
                this.parent = parent;
            }

            public PropertySpec.Builder empty() {
                parent.setterCode = CodeBlock.of(";").toBuilder();
                return parent;
            }

            /**
             * @param controlFlow the control flow construct and its code, such as "if (foo == 5)".
             *                    Shouldn't contain braces or newline characters.
             */
            public SetterBuilder beginControlFlow(String controlFlow, Object... args) {
                code.beginControlFlow(controlFlow, args);
                return this;
            }

            /**
             * @param controlFlow the control flow construct and its code, such as "else if (foo == 10)".
             *                    Shouldn't contain braces or newline characters.
             */
            public SetterBuilder nextControlFlow(String controlFlow, Object... args) {
                code.nextControlFlow(controlFlow, args);
                return this;
            }

            public SetterBuilder endControlFlow() {
                code.endControlFlow();
                return this;
            }

            /**
             * @param controlFlow the optional control flow construct and its code, such as
             *                    "while(foo == 20)". Only used for "do/while" control flows.
             */
            public SetterBuilder endControlFlow(String controlFlow, Object... args) {
                code.endControlFlow(controlFlow, args);
                return this;
            }

            public SetterBuilder addStatement(String format, Object... args) {
                code.addStatement(format, args);
                return this;
            }

            public SetterBuilder addStatement(CodeBlock codeBlock) {
                code.addStatement(codeBlock);
                return this;
            }

            public PropertySpec.Builder endSetter() {
                parent.setterCode = code;
                return parent;
            }
        }

    }
}
