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

import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;

import static me.nickac.cspoet.Util.checkArgument;
import static me.nickac.cspoet.Util.checkState;

/**
 * A generated class, interface, or enum declaration.
 */
public final class TypeSpec {
    public final Kind kind;
    public final String name;
    public final CodeBlock anonymousTypeArguments;
    public final CodeBlock javadoc;
    public final List<AttributeSpec> annotations;
    public final Set<CSharpModifier> modifiers;
    public final List<TypeVariableName> typeVariables;
    public final TypeName superclass;
    public final List<TypeName> superinterfaces;
    public final Map<String, TypeSpec> enumConstants;
    public final List<FieldSpec> fieldSpecs;
    public final CodeBlock staticBlock;
    public final CodeBlock initializerBlock;
    public final List<MethodSpec> methodSpecs;
    public final List<CodeBlock> miscCodeBlocks;
    public final List<PropertySpec> propertySpecs;
    public final List<TypeSpec> typeSpecs;
    public final List<Element> originatingElements;

    private TypeSpec(Builder builder) {
        this.kind = builder.kind;
        this.name = builder.name;
        this.anonymousTypeArguments = builder.anonymousTypeArguments;
        this.javadoc = builder.javadoc.build();
        this.annotations = Util.immutableList(builder.annotations);
        this.modifiers = Util.immutableSet(builder.modifiers);
        this.typeVariables = Util.immutableList(builder.typeVariables);
        this.superclass = builder.superclass;
        this.superinterfaces = Util.immutableList(builder.superinterfaces);
        this.enumConstants = Util.immutableMap(builder.enumConstants);
        this.fieldSpecs = Util.immutableList(builder.fieldSpecs);
        this.staticBlock = builder.staticBlock.build();
        this.initializerBlock = builder.initializerBlock.build();
        this.methodSpecs = Util.immutableList(builder.methodSpecs);
        this.propertySpecs = Util.immutableList(builder.propertySpecs);
        this.typeSpecs = Util.immutableList(builder.typeSpecs);
        this.miscCodeBlocks = Util.immutableList(builder.miscCodeBlocks);

        List<Element> originatingElementsMutable = new ArrayList<>();
        originatingElementsMutable.addAll(builder.originatingElements);
        for (TypeSpec typeSpec: builder.typeSpecs) {
            originatingElementsMutable.addAll(typeSpec.originatingElements);
        }
        this.originatingElements = Util.immutableList(originatingElementsMutable);
    }

    /**
     * Creates a dummy type spec for type-resolution only (in CodeWriter)
     * while emitting the type declaration but before entering the type body.
     */
    private TypeSpec(TypeSpec type) {
        assert type.anonymousTypeArguments == null;
        this.kind = type.kind;
        this.name = type.name;
        this.anonymousTypeArguments = null;
        this.javadoc = type.javadoc;
        this.annotations = Collections.emptyList();
        this.modifiers = Collections.emptySet();
        this.typeVariables = Collections.emptyList();
        this.superclass = null;
        this.superinterfaces = Collections.emptyList();
        this.enumConstants = Collections.emptyMap();
        this.fieldSpecs = Collections.emptyList();
        this.staticBlock = type.staticBlock;
        this.initializerBlock = type.initializerBlock;
        this.methodSpecs = Collections.emptyList();
        this.typeSpecs = Collections.emptyList();
        this.originatingElements = Collections.emptyList();
        this.propertySpecs = Collections.emptyList();
        this.miscCodeBlocks = Collections.emptyList();
    }

    public static Builder classBuilder(String name) {
        return new Builder(Kind.CLASS, Util.checkNotNull(name, "name == null"), null);
    }

    public static Builder classBuilder(ClassName className) {
        return classBuilder(Util.checkNotNull(className, "className == null").simpleName());
    }

    public static Builder interfaceBuilder(String name) {
        return new Builder(Kind.INTERFACE, Util.checkNotNull(name, "name == null"), null);
    }

    public static Builder interfaceBuilder(ClassName className) {
        return interfaceBuilder(Util.checkNotNull(className, "className == null").simpleName());
    }

    public static Builder enumBuilder(String name) {
        return new Builder(Kind.ENUM, Util.checkNotNull(name, "name == null"), null);
    }

    public static Builder enumBuilder(ClassName className) {
        return enumBuilder(Util.checkNotNull(className, "className == null").simpleName());
    }

    public static Builder anonymousClassBuilder(String typeArgumentsFormat, Object... args) {
        return anonymousClassBuilder(CodeBlock.builder()
                .add(typeArgumentsFormat, args)
                .build());
    }

    public static Builder anonymousClassBuilder(CodeBlock typeArguments) {
        return new Builder(Kind.CLASS, null, typeArguments);
    }

    public static Builder annotationBuilder(String name) {
        return new Builder(Kind.ANNOTATION, Util.checkNotNull(name, "name == null"), null);
    }

    public static Builder annotationBuilder(ClassName className) {
        return annotationBuilder(Util.checkNotNull(className, "className == null").simpleName());
    }

    public boolean hasModifier(CSharpModifier modifier) {
        return modifiers.contains(modifier);
    }

    public Builder toBuilder() {
        Builder builder = new Builder(kind, name, anonymousTypeArguments);
        builder.javadoc.add(javadoc);
        builder.annotations.addAll(annotations);
        builder.modifiers.addAll(modifiers);
        builder.typeVariables.addAll(typeVariables);
        builder.superclass = superclass;
        builder.superinterfaces.addAll(superinterfaces);
        builder.enumConstants.putAll(enumConstants);
        builder.fieldSpecs.addAll(fieldSpecs);
        builder.propertySpecs.addAll(propertySpecs);
        builder.methodSpecs.addAll(methodSpecs);
        builder.typeSpecs.addAll(typeSpecs);
        builder.initializerBlock.add(initializerBlock);
        builder.staticBlock.add(staticBlock);
        builder.miscCodeBlocks.addAll(miscCodeBlocks);
        return builder;
    }

    @SuppressWarnings("Duplicates")
    void emit(CodeWriter codeWriter, String enumName, Set<CSharpModifier> implicitModifiers)
            throws IOException {
        // Nested classes interrupt wrapped line indentation. Stash the current wrapping state and put
        // it back afterwards when this type is complete.
        int previousStatementLine = codeWriter.statementLine;
        codeWriter.statementLine = -1;

        try {
            if (enumName != null) {
                codeWriter.emitJavadoc(javadoc);
                codeWriter.emitAnnotations(annotations, false);
                codeWriter.emit("$L", enumName);
                if (!anonymousTypeArguments.formatParts.isEmpty()) {
                    codeWriter.emit("(");
                    codeWriter.emit(anonymousTypeArguments);
                    codeWriter.emit(")");
                }
                if (fieldSpecs.isEmpty() && methodSpecs.isEmpty() && typeSpecs.isEmpty()) {
                    return; // Avoid unnecessary braces "{}".
                }
                codeWriter.emit(" {\n");
            } else if (anonymousTypeArguments != null) {
                TypeName supertype = !superinterfaces.isEmpty() ? superinterfaces.get(0) : superclass;
                codeWriter.emit("new $T(", supertype);
                codeWriter.emit(anonymousTypeArguments);
                codeWriter.emit(") {\n");
            } else {
                // Push an empty type (specifically without nested types) for type-resolution.
                codeWriter.pushType(new TypeSpec(this));

                codeWriter.emitJavadoc(javadoc);
                codeWriter.emitAnnotations(annotations, false);
                codeWriter.emitModifiers(modifiers, Util.union(implicitModifiers, kind.asMemberModifiers));
                if (kind == Kind.ANNOTATION) {
                    codeWriter.emit("$L $L", "@interface", name);
                } else {
                    codeWriter.emit("$L $L", kind.name().toLowerCase(Locale.US), name);
                }
                codeWriter.emitTypeVariables(typeVariables);

                List<TypeName> extendsTypes;
                List<TypeName> implementsTypes;
                if (kind == Kind.INTERFACE) {
                    extendsTypes = superinterfaces;
                    implementsTypes = Collections.emptyList();
                } else {
                    extendsTypes = superclass.equals(ClassName.OBJECT)
                            ? Collections.emptyList()
                            : Collections.singletonList(superclass);
                    implementsTypes = superinterfaces;
                }

                boolean hasEmitedClassSeparator = false;

                if (!extendsTypes.isEmpty()) {
                    if (!hasEmitedClassSeparator) {
                        codeWriter.emit(" :");
                        hasEmitedClassSeparator = true;
                    }
                    boolean firstType = true;
                    for (TypeName type: extendsTypes) {
                        if (!firstType) codeWriter.emit(", ");
                        codeWriter.emit(" $T", type);
                        firstType = false;
                    }
                }

                if (!implementsTypes.isEmpty()) {
                    if (!hasEmitedClassSeparator) {
                        codeWriter.emit(" :");
                        hasEmitedClassSeparator = true;
                    }
                    boolean firstType = true;
                    for (TypeName type: implementsTypes) {
                        if (!firstType) codeWriter.emit(", ");
                        codeWriter.emit(" $T", type);
                        firstType = false;
                    }
                }

                codeWriter.popType();

                codeWriter.emit(" {\n");
            }

            codeWriter.pushType(this);
            codeWriter.indent();
            boolean firstMember = true;
            for (Iterator<Map.Entry<String, TypeSpec>> i = enumConstants.entrySet().iterator();
                 i.hasNext(); ) {
                Map.Entry<String, TypeSpec> enumConstant = i.next();
                if (!firstMember) codeWriter.emit("\n");
                enumConstant.getValue().emit(codeWriter, enumConstant.getKey(), Collections.emptySet());
                firstMember = false;
                if (i.hasNext()) {
                    codeWriter.emit(",\n");
                } else if (!fieldSpecs.isEmpty() || !methodSpecs.isEmpty() || !typeSpecs.isEmpty()) {
                    codeWriter.emit(";\n");
                } else {
                    codeWriter.emit("\n");
                }
            }

            // Static fields.
            for (FieldSpec fieldSpec: fieldSpecs) {
                if (!fieldSpec.hasModifier(CSharpModifier.STATIC)) continue;
                if (!firstMember) codeWriter.emit("\n");
                fieldSpec.emit(codeWriter, kind.implicitFieldModifiers);
                firstMember = false;
            }

            if (!staticBlock.isEmpty()) {
                if (!firstMember) codeWriter.emit("\n");
                codeWriter.emit(staticBlock);
                firstMember = false;
            }

            // Non-static fields.
            for (FieldSpec fieldSpec: fieldSpecs) {
                if (fieldSpec.hasModifier(CSharpModifier.STATIC)) continue;
                if (!firstMember) codeWriter.emit("\n");
                fieldSpec.emit(codeWriter, kind.implicitFieldModifiers);
                firstMember = false;
            }

            // Initializer block.
            if (!initializerBlock.isEmpty()) {
                if (!firstMember) codeWriter.emit("\n");
                codeWriter.emit(initializerBlock);
                firstMember = false;
            }

            // Methods (static and non-static).
            for (MethodSpec methodSpec: methodSpecs) {
                if (!firstMember) codeWriter.emit("\n");
                methodSpec.emit(codeWriter, name, kind.implicitMethodModifiers);
                firstMember = false;
            }

            // Properties
            for (PropertySpec propertySpec: propertySpecs) {
                if (!firstMember) codeWriter.emit("\n");
                propertySpec.emit(codeWriter, name, kind.implicitMethodModifiers);
                firstMember = false;
            }

            // Misc Properties
            for (CodeBlock codeBlock: miscCodeBlocks) {
                if (!firstMember) codeWriter.emit("\n");
                codeWriter.emit(codeBlock);
                firstMember = false;
            }

            // Types.
            for (TypeSpec typeSpec: typeSpecs) {
                if (!firstMember) codeWriter.emit("\n");
                typeSpec.emit(codeWriter, null, kind.implicitTypeModifiers);
                firstMember = false;
            }

            codeWriter.unindent();
            codeWriter.popType();

            codeWriter.emit("}");
            if (enumName == null && anonymousTypeArguments == null) {
                codeWriter.emit("\n"); // If this type isn't also a value, include a trailing newline.
            }
        } finally {
            codeWriter.statementLine = previousStatementLine;
        }
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
        StringBuilder out = new StringBuilder();
        try {
            CodeWriter codeWriter = new CodeWriter(out);
            emit(codeWriter, null, Collections.emptySet());
            return out.toString();
        } catch (IOException e) {
            throw new AssertionError();
        }
    }

    public enum Kind {
        CLASS(
                Collections.emptySet(),
                Collections.emptySet(),
                Collections.emptySet(),
                Collections.emptySet()),

        INTERFACE(
                Util.immutableSet(Arrays.asList(CSharpModifier.PUBLIC, CSharpModifier.STATIC, CSharpModifier.READONLY)),
                Util.immutableSet(Arrays.asList(CSharpModifier.PUBLIC, CSharpModifier.ABSTRACT)),
                Util.immutableSet(Arrays.asList(CSharpModifier.PUBLIC, CSharpModifier.STATIC)),
                Util.immutableSet(Collections.singletonList(CSharpModifier.STATIC))),

        ENUM(
                Collections.emptySet(),
                Collections.emptySet(),
                Collections.emptySet(),
                Collections.singleton(CSharpModifier.STATIC)),

        ANNOTATION(
                Util.immutableSet(Arrays.asList(CSharpModifier.PUBLIC, CSharpModifier.STATIC, CSharpModifier.READONLY)),
                Util.immutableSet(Arrays.asList(CSharpModifier.PUBLIC, CSharpModifier.ABSTRACT)),
                Util.immutableSet(Arrays.asList(CSharpModifier.PUBLIC, CSharpModifier.STATIC)),
                Util.immutableSet(Collections.singletonList(CSharpModifier.STATIC)));

        private final Set<CSharpModifier> implicitFieldModifiers;
        private final Set<CSharpModifier> implicitMethodModifiers;
        private final Set<CSharpModifier> implicitTypeModifiers;
        private final Set<CSharpModifier> asMemberModifiers;

        Kind(Set<CSharpModifier> implicitFieldModifiers,
             Set<CSharpModifier> implicitMethodModifiers,
             Set<CSharpModifier> implicitTypeModifiers,
             Set<CSharpModifier> asMemberModifiers) {
            this.implicitFieldModifiers = implicitFieldModifiers;
            this.implicitMethodModifiers = implicitMethodModifiers;
            this.implicitTypeModifiers = implicitTypeModifiers;
            this.asMemberModifiers = asMemberModifiers;
        }
    }

    public static final class Builder {
        private final Kind kind;
        private final String name;
        private final CodeBlock anonymousTypeArguments;

        private final CodeBlock.Builder javadoc = CodeBlock.builder();
        private final List<AttributeSpec> annotations = new ArrayList<>();
        private final List<CSharpModifier> modifiers = new ArrayList<>();
        private final List<TypeVariableName> typeVariables = new ArrayList<>();
        private final List<TypeName> superinterfaces = new ArrayList<>();
        private final Map<String, TypeSpec> enumConstants = new LinkedHashMap<>();
        private final List<FieldSpec> fieldSpecs = new ArrayList<>();
        private final CodeBlock.Builder staticBlock = CodeBlock.builder();
        private final CodeBlock.Builder initializerBlock = CodeBlock.builder();
        private final List<MethodSpec> methodSpecs = new ArrayList<>();
        private final List<CodeBlock> miscCodeBlocks = new ArrayList<>();
        private final List<PropertySpec> propertySpecs = new ArrayList<>();
        private final List<TypeSpec> typeSpecs = new ArrayList<>();
        private final List<Element> originatingElements = new ArrayList<>();
        private TypeName superclass = ClassName.OBJECT;

        private Builder(Kind kind, String name,
                        CodeBlock anonymousTypeArguments) {
            checkArgument(name == null || SourceVersion.isName(name), "not a valid name: %s", name);
            this.kind = kind;
            this.name = name;
            this.anonymousTypeArguments = anonymousTypeArguments;
        }

        public Builder addJavadoc(String format, Object... args) {
            javadoc.add(format, args);
            return this;
        }

        public Builder addJavadoc(CodeBlock block) {
            javadoc.add(block);
            return this;
        }

        public Builder addAnnotations(Iterable<AttributeSpec> annotationSpecs) {
            checkArgument(annotationSpecs != null, "annotationSpecs == null");
            for (AttributeSpec attributeSpec: annotationSpecs) {
                this.annotations.add(attributeSpec);
            }
            return this;
        }

        public Builder addAnnotation(AttributeSpec attributeSpec) {
            Util.checkNotNull(attributeSpec, "attributeSpec == null");
            this.annotations.add(attributeSpec);
            return this;
        }

        public Builder addAnnotation(ClassName annotation) {
            return addAnnotation(AttributeSpec.builder(annotation).build());
        }

        public Builder addAnnotation(Class<?> annotation) {
            return addAnnotation(ClassName.get(annotation));
        }

        public Builder addModifiers(CSharpModifier... modifiers) {
            checkState(anonymousTypeArguments == null, "forbidden on anonymous types.");
            for (CSharpModifier modifier: modifiers) {
                checkArgument(modifier != null, "modifiers contain null");
                this.modifiers.add(modifier);
            }
            return this;
        }

        public Builder addTypeVariables(Iterable<TypeVariableName> typeVariables) {
            checkState(anonymousTypeArguments == null, "forbidden on anonymous types.");
            checkArgument(typeVariables != null, "typeVariables == null");
            for (TypeVariableName typeVariable: typeVariables) {
                this.typeVariables.add(typeVariable);
            }
            return this;
        }

        public Builder addTypeVariable(TypeVariableName typeVariable) {
            checkState(anonymousTypeArguments == null, "forbidden on anonymous types.");
            typeVariables.add(typeVariable);
            return this;
        }

        public Builder superclass(TypeName superclass) {
            checkState(this.kind == Kind.CLASS, "only classes have super classes, not " + this.kind);
            checkState(this.superclass == ClassName.OBJECT,
                    "superclass already set to " + this.superclass);
            checkArgument(!superclass.isPrimitive(), "superclass may not be a primitive");
            this.superclass = superclass;
            return this;
        }

        public Builder superclass(Type superclass) {
            return superclass(TypeName.get(superclass));
        }

        public Builder addSuperinterfaces(Iterable<? extends TypeName> superinterfaces) {
            checkArgument(superinterfaces != null, "superinterfaces == null");
            for (TypeName superinterface: superinterfaces) {
                addSuperinterface(superinterface);
            }
            return this;
        }

        public Builder addSuperinterface(TypeName superinterface) {
            checkArgument(superinterface != null, "superinterface == null");
            this.superinterfaces.add(superinterface);
            return this;
        }

        public Builder addSuperinterface(Type superinterface) {
            return addSuperinterface(TypeName.get(superinterface));
        }

        public Builder addEnumConstant(String name) {
            return addEnumConstant(name, anonymousClassBuilder("").build());
        }

        public Builder addEnumConstant(String name, TypeSpec typeSpec) {
            checkState(kind == Kind.ENUM, "%s is not enum", this.name);
            checkArgument(typeSpec.anonymousTypeArguments != null,
                    "enum constants must have anonymous type arguments");
            checkArgument(SourceVersion.isName(name), "not a valid enum constant: %s", name);
            enumConstants.put(name, typeSpec);
            return this;
        }

        public Builder addFields(Iterable<FieldSpec> fieldSpecs) {
            checkArgument(fieldSpecs != null, "fieldSpecs == null");
            for (FieldSpec fieldSpec: fieldSpecs) {
                addField(fieldSpec);
            }
            return this;
        }

        public Builder addField(FieldSpec fieldSpec) {
            if (kind == Kind.INTERFACE || kind == Kind.ANNOTATION) {
                Util.requireExactlyOneOf(fieldSpec.modifiers, CSharpModifier.PUBLIC, CSharpModifier.PRIVATE);
                Set<CSharpModifier> check = EnumSet.of(CSharpModifier.STATIC, CSharpModifier.READONLY);
                checkState(fieldSpec.modifiers.containsAll(check), "%s %s.%s requires modifiers %s",
                        kind, name, fieldSpec.name, check);
            }
            fieldSpecs.add(fieldSpec);
            return this;
        }

        public Builder addField(TypeName type, String name, CSharpModifier... modifiers) {
            return addField(FieldSpec.builder(type, name, modifiers).build());
        }

        public Builder addField(Type type, String name, CSharpModifier... modifiers) {
            return addField(TypeName.get(type), name, modifiers);
        }

        public Builder addStaticBlock(CodeBlock block) {
            staticBlock.beginControlFlow("static").add(block).endControlFlow();
            return this;
        }

        public Builder addInitializerBlock(CodeBlock block) {
            if ((kind != Kind.CLASS && kind != Kind.ENUM)) {
                throw new UnsupportedOperationException(kind + " can't have initializer blocks");
            }
            initializerBlock.add("{\n")
                    .indent()
                    .add(block)
                    .unindent()
                    .add("}\n");
            return this;
        }

        public Builder addMethods(Iterable<MethodSpec> methodSpecs) {
            checkArgument(methodSpecs != null, "methodSpecs == null");
            for (MethodSpec methodSpec: methodSpecs) {
                addMethod(methodSpec);
            }
            return this;
        }

        public Builder addMethod(MethodSpec methodSpec) {
            Util.requireExactlyOneOrNoneOf(methodSpec.modifiers, CSharpModifier.EXPLICIT, CSharpModifier.IMPLICIT);
            if (kind == Kind.INTERFACE) {
                Util.requireExactlyOneOf(methodSpec.modifiers, CSharpModifier.ABSTRACT, CSharpModifier.STATIC);
                Util.requireExactlyOneOf(methodSpec.modifiers, CSharpModifier.PUBLIC, CSharpModifier.PRIVATE);
            } else if (kind == Kind.ANNOTATION) {
                checkState(methodSpec.modifiers.equals(kind.implicitMethodModifiers),
                        "%s %s.%s requires modifiers %s",
                        kind, name, methodSpec.name, kind.implicitMethodModifiers);
            }
            if (kind != Kind.ANNOTATION) {
                checkState(methodSpec.defaultValue == null, "%s %s.%s cannot have a default value",
                        kind, name, methodSpec.name);
            }
            if (methodSpec.hasModifier(CSharpModifier.OPERATOR)) {
                Util.requireExactlyOneOf(methodSpec.modifiers, CSharpModifier.STATIC);
            }
            methodSpecs.add(methodSpec);
            return this;
        }

        public Builder addTypes(Iterable<TypeSpec> typeSpecs) {
            checkArgument(typeSpecs != null, "typeSpecs == null");
            for (TypeSpec typeSpec: typeSpecs) {
                addType(typeSpec);
            }
            return this;
        }

        public Builder addType(TypeSpec typeSpec) {
            checkArgument(typeSpec.modifiers.containsAll(kind.implicitTypeModifiers),
                    "%s %s.%s requires modifiers %s", kind, name, typeSpec.name,
                    kind.implicitTypeModifiers);
            typeSpecs.add(typeSpec);
            return this;
        }

        public Builder addOriginatingElement(Element originatingElement) {
            originatingElements.add(originatingElement);
            return this;
        }

        public Builder addProperty(PropertySpec propertySpec) {
            //TODO: Add property checks
            propertySpecs.add(propertySpec);
            return this;
        }

        public Builder addMiscCodeblock(String block) {
            miscCodeBlocks.add(CodeBlock.of(block));
            return this;
        }

        public Builder addMiscCodeblock(CodeBlock block) {
            miscCodeBlocks.add(block);
            return this;
        }

        public TypeSpec build() {
            checkArgument(kind != Kind.ENUM || !enumConstants.isEmpty(),
                    "at least one enum constant is required for %s", name);

            boolean isAbstract = modifiers.contains(CSharpModifier.ABSTRACT) || kind != Kind.CLASS;
            for (MethodSpec methodSpec: methodSpecs) {
                checkArgument(isAbstract || !methodSpec.hasModifier(CSharpModifier.ABSTRACT),
                        "non-abstract type %s cannot declare abstract method %s", name, methodSpec.name);
            }

            boolean superclassIsObject = superclass.equals(ClassName.OBJECT);
            int interestingSupertypeCount = (superclassIsObject ? 0 : 1) + superinterfaces.size();
            checkArgument(anonymousTypeArguments == null || interestingSupertypeCount <= 1,
                    "anonymous type has too many supertypes");

            return new TypeSpec(this);
        }
    }
}
