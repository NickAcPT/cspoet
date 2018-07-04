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

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import javax.lang.model.element.Modifier;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.google.common.truth.Truth.assertThat;

@RunWith(JUnit4.class)
public final class CSharpFileTest {

    @Test
    public void canGeneratePluginMessageListenerClass() {
        TypeName remoteObject = TypeVariableName.get("RemoteObject");

        MethodSpec messageReceived = MethodSpec.methodBuilder("OnPluginMessageReceived")
                .addParameter(TypeVariableName.get("string"), "arg0")
                .addParameter(TypeVariableName.get("Player"), "arg1")
                .addParameter(TypeVariableName.get("byte[]"), "arg2")
                .addModifiers(CSharpModifier.PUBLIC)
                .addStatement("RunIfRemoteNotNull(r => r.@onPluginMessageReceived(arg0, arg1, arg2).ToManaged() )")
                .build();

        MethodSpec classCtor = MethodSpec.constructorBuilder()
                .addModifiers(CSharpModifier.PROTECTED)
                .addParameter(TypeVariableName.get("JObject"), "obj")
                .addStatement("InnerJsonObject = obj")
                .build();

        MethodSpec jObjectOperator = MethodSpec.methodBuilder("PluginMessageListener")
                .addModifiers(CSharpModifier.PUBLIC, CSharpModifier.STATIC, CSharpModifier.EXPLICIT, CSharpModifier.OPERATOR)
                .addParameter(TypeVariableName.get("JObject"), "obj")
                .addStatement("return new PluginMessageListener(obj)")
                .build();

        MethodSpec fromRemoteObject = MethodSpec.methodBuilder("FromRemoteObject")
                .addModifiers(CSharpModifier.PUBLIC, CSharpModifier.STATIC)
                .addParameter(TypeVariableName.get("RemoteObject"), "obj")
                .returns(TypeVariableName.get("PluginMessageListener"))
                .addStatement("return new PluginMessageListener(obj.InnerJsonObject)")
                .build();

        PropertySpec xProperty = PropertySpec.propertyBuilder("X")
                .addModifier(CSharpModifier.PUBLIC)
                .returns(TypeName.DOUBLE)
                .getter()
                .addStatement("return RunIfRemoteNotNull<double>(r => (double) r.@getX().ToManaged())")
                .endGetter()
                .build();

        TypeSpec clazz = TypeSpec.classBuilder("PluginMessageListener")
                .addModifiers(CSharpModifier.PUBLIC)
                .addSuperinterface(remoteObject)
                .addMethod(messageReceived)
                .addMethod(classCtor)
                .addMethod(jObjectOperator)
                .addMethod(fromRemoteObject)
                .addProperty(xProperty)
                .build();

        CSharpFile example = CSharpFile.builder("", clazz)
                .addUsing("System.Collections.Generic")
                .addUsing("System.IO")
                .addUsing("Newtonsoft.Json.Linq")
                .addUsing("NickAc.ManagedInterface.Wrapper")
                .build();


        assertThat(example.toString()).isEqualTo("using System.Collections.Generic;\n" +
                "using System.IO;\n" +
                "using Newtonsoft.Json.Linq;\n" +
                "using NickAc.ManagedInterface.Wrapper;\n" +
                "\n" +
                "public class PluginMessageListener : RemoteObject {\n" +
                "\tpublic void OnPluginMessageReceived(string arg0, Player arg1, byte[] arg2) {\n" +
                "\t\tRunIfRemoteNotNull(r => r.@onPluginMessageReceived(arg0, arg1, arg2).ToManaged() );\n" +
                "\t}\n" +
                "\n" +
                "\tprotected PluginMessageListener(JObject obj) {\n" +
                "\t\tInnerJsonObject = obj;\n" +
                "\t}\n" +
                "\n" +
                "\tpublic static explicit operator PluginMessageListener(JObject obj) {\n" +
                "\t\treturn new PluginMessageListener(obj);\n" +
                "\t}\n" +
                "\n" +
                "\tpublic static PluginMessageListener FromRemoteObject(RemoteObject obj) {\n" +
                "\t\treturn new PluginMessageListener(obj.InnerJsonObject);\n" +
                "\t}\n" +
                "\n" +
                "\tpublic double X {\n" +
                "\t\tget {\n" +
                "\t\t\treturn RunIfRemoteNotNull<double>(r => (double) r.@getX().ToManaged());\n" +
                "\t\t}\n" +
                "\t}\n" +
                "}\n");
    }
}
