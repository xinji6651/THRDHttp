package com.rxhttp.compiler

import com.squareup.javapoet.*
import rxhttp.wrapper.annotation.Parser
import java.io.IOException
import java.util.*
import javax.annotation.processing.Filer
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Modifier
import javax.lang.model.element.TypeElement
import javax.lang.model.type.MirroredTypesException
import javax.lang.model.type.TypeMirror
import kotlin.collections.ArrayList

class ParserAnnotatedClass {

    private val mElementMap = LinkedHashMap<String, TypeElement>()
    private val mTypeMap = LinkedHashMap<String, List<TypeMirror>>()

    fun add(typeElement: TypeElement) {
        val annotation = typeElement.getAnnotation(Parser::class.java)
        val name: String = annotation.name
        require(name.isNotEmpty()) {
            String.format("methodName() in @%s for class %s is null or empty! that's not allowed",
                Parser::class.java.simpleName, typeElement.qualifiedName.toString())
        }
        try {
            annotation.wrappers
        } catch (e: MirroredTypesException) {
            val typeMirrors = e.typeMirrors
            mTypeMap[name] = typeMirrors
        }
        mElementMap[name] = typeElement
    }

    fun getMethodList(filer: Filer): List<MethodSpec> {
        val t = TypeVariableName.get("T")
        val callName = ClassName.get("okhttp3", "Call")
        val okHttpClientName = ClassName.get("okhttp3", "OkHttpClient")
        val responseName = ClassName.get("okhttp3", "Response")
        val httpSenderName = ClassName.get("rxhttp", "HttpSender")
        val requestName = ClassName.get("okhttp3", "Request")
        val parserName = ClassName.get("rxhttp.wrapper.parse", "Parser")
        val progressName = ClassName.get("rxhttp.wrapper.entity", "Progress")
        val progressTName = ClassName.get("rxhttp.wrapper.entity", "ProgressT")
        val typeName = TypeName.get(String::class.java)
        val progressTStringName = ParameterizedTypeName.get(progressTName, typeName)
        val parserTName = ParameterizedTypeName.get(parserName, t)
        val methodList = ArrayList<MethodSpec>()

        methodList.add(
            MethodSpec.methodBuilder("execute")
                .addModifiers(Modifier.PUBLIC)
                .addException(IOException::class.java)
                .addStatement("doOnStart()")
                .addStatement("return newCall().execute()")
                .returns(responseName)
                .build())

        methodList.add(
            MethodSpec.methodBuilder("execute")
                .addModifiers(Modifier.PUBLIC)
                .addTypeVariable(t)
                .addException(IOException::class.java)
                .addParameter(parserTName, "parser")
                .addStatement("return parser.onParse(execute())", httpSenderName)
                .returns(t)
                .build())

        methodList.add(
            MethodSpec.methodBuilder("newCall")
                .addModifiers(Modifier.PUBLIC)
                .addStatement("return newCall(getOkHttpClient())")
                .returns(callName)
                .build())

        methodList.add(
            MethodSpec.methodBuilder("newCall")
                .addModifiers(Modifier.PUBLIC)
                .addParameter(okHttpClientName, "okHttp")
                .addStatement("return \$T.newCall(okHttp, buildRequest())", httpSenderName)
                .returns(callName)
                .build())

        methodList.add(
            MethodSpec.methodBuilder("buildRequest")
                .addAnnotation(Override::class.java)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addStatement("doOnStart()")
                .addStatement("return param.buildRequest()")
                .returns(requestName)
                .build())

        methodList.add(
            MethodSpec.methodBuilder("doOnStart")
                .addJavadoc("???????????????????????????????????????????????????????????????\n")
                .addStatement("setConverter(param)")
                .addStatement("addDefaultDomainIfAbsent(param)")
                .build())

        if (isDependenceRxJava()) {
            val schedulerName = getClassName("Scheduler")
            val observableName = getClassName("Observable")
            val consumerName = getClassName("Consumer")
            methodList.add(
                MethodSpec.methodBuilder("subscribeOn")
                    .addModifiers(Modifier.PUBLIC)
                    .addParameter(schedulerName, "scheduler")
                    .addStatement("this.scheduler=scheduler")
                    .addStatement("return (R)this")
                    .returns(r)
                    .build())

            methodList.add(
                MethodSpec.methodBuilder("subscribeOnCurrent")
                    .addJavadoc("??????????????????????????????\n")
                    .addModifiers(Modifier.PUBLIC)
                    .addStatement("this.scheduler=null")
                    .addStatement("return (R)this")
                    .returns(r)
                    .build())

            methodList.add(
                MethodSpec.methodBuilder("subscribeOnIo")
                    .addModifiers(Modifier.PUBLIC)
                    .addStatement("this.scheduler=Schedulers.io()")
                    .addStatement("return (R)this")
                    .returns(r)
                    .build())

            methodList.add(
                MethodSpec.methodBuilder("subscribeOnComputation")
                    .addModifiers(Modifier.PUBLIC)
                    .addStatement("this.scheduler=Schedulers.computation()")
                    .addStatement("return (R)this")
                    .returns(r)
                    .build())

            methodList.add(
                MethodSpec.methodBuilder("subscribeOnNewThread")
                    .addModifiers(Modifier.PUBLIC)
                    .addStatement("this.scheduler=Schedulers.newThread()")
                    .addStatement("return (R)this")
                    .returns(r)
                    .build())

            methodList.add(
                MethodSpec.methodBuilder("subscribeOnSingle")
                    .addModifiers(Modifier.PUBLIC)
                    .addStatement("this.scheduler=Schedulers.single()")
                    .addStatement("return (R)this")
                    .returns(r)
                    .build())

            methodList.add(
                MethodSpec.methodBuilder("subscribeOnTrampoline")
                    .addModifiers(Modifier.PUBLIC)
                    .addStatement("this.scheduler=Schedulers.trampoline()")
                    .addStatement("return (R)this")
                    .returns(r)
                    .build())

            val observableTName = ParameterizedTypeName.get(observableName, t)
            val observableStringName = ParameterizedTypeName.get(observableName, typeName)
            val consumerProgressName = ParameterizedTypeName.get(consumerName, progressName)

            methodList.add(
                MethodSpec.methodBuilder("asParser")
                    .addAnnotation(Override::class.java)
                    .addModifiers(Modifier.PUBLIC)
                    .addTypeVariable(t)
                    .addParameter(parserTName, "parser")
                    .addStatement("""
                        doOnStart();
                    Observable<T> observable = new ObservableHttp<T>(okClient, param, parser);
                    if (scheduler != null) {
                        observable = observable.subscribeOn(scheduler);
                    }
                    return observable
                """.trimIndent())
                    .returns(observableTName)
                    .build())

            methodList.add(
                MethodSpec.methodBuilder("asDownload")
                    .addAnnotation(Override::class.java)
                    .addJavadoc("""
                         ???????????????????????????????????????                                                                 
                         @param destPath           ??????????????????                                              
                         @param observeOnScheduler ?????????????????????????????????null?????????????????????????????????(?????????)??????                   
                         @param progressConsumer   ????????????                                                
                         @return Observable
                    """.trimIndent())
                    .addModifiers(Modifier.PUBLIC)
                    .addParameter(String::class.java, "destPath")
                    .addParameter(schedulerName, "observeOnScheduler")
                    .addParameter(consumerProgressName, "progressConsumer")
                    .addStatement("""
                        doOnStart();
                    Observable<Progress> observable = new ObservableDownload(okClient, param, destPath, breakDownloadOffSize);
                    if (scheduler != null)
                        observable = observable.subscribeOn(scheduler);
                    if (observeOnScheduler != null) {
                        observable = observable.observeOn(observeOnScheduler);
                    }
                    return observable.doOnNext(progressConsumer)
                        .filter(progress -> progress instanceof ProgressT)
                        .map(progress -> ((${"$"}T) progress).getResult())
                """.trimIndent(), progressTStringName)
                    .returns(observableStringName)
                    .build())
        }


        val rxHttpExtensions = RxHttpExtensions()

        //???????????????????????????
        for ((parserAlias, typeElement) in mElementMap) {

            //??????RxHttp????????????(kotlin???????????????)
            rxHttpExtensions.generateRxHttpExtendFun(typeElement, parserAlias)

            if (isDependenceRxJava()) { //?????????RxJava????????????????????????asXxx??????
                //onParser??????????????????
                val returnTypeMirror = getOnParserFunReturnType(typeElement) ?: continue
                val onParserFunReturnType = TypeName.get(returnTypeMirror)

                val typeVariableNames = ArrayList<TypeVariableName>()
                typeElement.typeParameters.forEach {
                    typeVariableNames.add(TypeVariableName.get(it))
                }

                //??????public????????????
                getConstructorFun(typeElement).forEach {
                    //?????????????????????????????????asXxx?????????????????????
                    val parameterList = ArrayList<ParameterSpec>()
                    var typeIndex = 0
                    it.parameters.forEach { variableNames ->
                        if (variableNames.asType().toString() == "java.lang.reflect.Type"
                            && typeIndex < typeVariableNames.size
                        ) {
                            //Type???????????????Class<T>??????
                            val classTypeName = ParameterizedTypeName.get(
                                ClassName.get(Class::class.java), typeVariableNames[typeIndex++])
                            val parameterSpec = ParameterSpec
                                .builder(classTypeName, variableNames.simpleName.toString())
                                .build()
                            parameterList.add(parameterSpec)
                        } else {
                            parameterList.add(ParameterSpec.get(variableNames))
                        }
                    }

                    //?????????
                    var methodName = "as$parserAlias"
                    //?????????
                    val methodBody =
                        "return asParser(new \$T${getTypeVariableString(typeVariableNames)}(${getParamsName(parameterList)}))"

                    //?????????as??????????????????(Observable<T>??????)
                    var asFunReturnType = ParameterizedTypeName.get(
                        getClassName("Observable"), onParserFunReturnType)
                    methodList.add(
                        MethodSpec.methodBuilder(methodName)
                            .addModifiers(Modifier.PUBLIC)
                            .addTypeVariables(typeVariableNames)
                            .addParameters(parameterList)
                            .addStatement(methodBody, ClassName.get(typeElement))  //????????????????????????
                            .returns(asFunReturnType)
                            .build())

                    var haveClassTypeParam = false
                    parameterList.forEach { p ->
                        if (p.type.toString().startsWith("java.lang.Class")) {
                            haveClassTypeParam = true
                        }
                    }
                    //???????????????Class????????????
                    if (typeVariableNames.isNotEmpty() && haveClassTypeParam) {

                        //??????????????????????????????Parser?????????wrappers??????
                        val wrapperTypes = mTypeMap[parserAlias]
                        wrapperTypes?.forEach { mirror ->

                            //1???asXxx???????????????
                            val wrapperClass = ClassName.bestGuess(mirror.toString())
                            val onParserFunReturnWrapperType = if (onParserFunReturnType is ParameterizedTypeName) {
                                //???????????????n?????????????????????????????????????????????
                                val typeNames = ArrayList<TypeName>()
                                for (type in onParserFunReturnType.typeArguments) {
                                    typeNames.add(ParameterizedTypeName.get(wrapperClass, type))
                                }
                                ParameterizedTypeName.get(onParserFunReturnType.rawType, *typeNames.toTypedArray())
                            } else {
                                ParameterizedTypeName.get(wrapperClass, onParserFunReturnType)
                            }
                            asFunReturnType = ParameterizedTypeName.get(getClassName("Observable"), onParserFunReturnWrapperType)

                            //2???asXxx?????????
                            val name = mirror.toString()
                            val simpleName = name.substring(name.lastIndexOf(".") + 1)
                            methodName = "as$parserAlias${simpleName}"

                            //3???asXxx?????????
                            val parameterizedType = ClassName.get("rxhttp.wrapper.entity", "ParameterizedTypeImpl")
                            val type = ClassName.get("java.lang.reflect", "Type")
                            val funBody = CodeBlock.builder()
                            val paramsName = StringBuilder()
                            //??????????????????????????????
                            parameterList.forEach { param ->
                                if (param.type.toString().startsWith("java.lang.Class")) {
                                    /*
                                     * Class???????????????????????????????????????????????????????????????
                                     * ?????????Type tTypeList = ParameterizedTypeImpl.get(List.class, tType);
                                     */
                                    val expression = "\$T ${param.name}$simpleName = \$T.get($simpleName.class, ${param.name})"
                                    funBody.addStatement(expression, type, parameterizedType)
                                    paramsName.append("${param.name}${simpleName}")
                                } else {
                                    paramsName.append(param.name)
                                }
                                paramsName.append(", ")
                            }
                            paramsName.delete(paramsName.length - 2, paramsName.length)
                            val returnStatement = "return asParser(new \$T${getTypeVariableString(typeVariableNames, mirror)}($paramsName))"
                            funBody.addStatement(returnStatement, ClassName.get(typeElement))

                            //4?????????asXxx??????
                            methodList.add(
                                MethodSpec.methodBuilder(methodName)
                                    .addModifiers(Modifier.PUBLIC)
                                    .addTypeVariables(typeVariableNames)
                                    .addParameters(parameterList)
                                    .addCode(funBody.build())  //????????????????????????
                                    .returns(asFunReturnType)
                                    .build())
                        }
                    }
                }
            }
        }
        rxHttpExtensions.generateClassFile(filer)
        return methodList
    }


    //??????????????????
    private fun getConstructorFun(typeElement: TypeElement): MutableList<ExecutableElement> {
        val funList = ArrayList<ExecutableElement>()
        typeElement.enclosedElements.forEach {
            if (it is ExecutableElement
                && it.kind == ElementKind.CONSTRUCTOR
                && it.getModifiers().contains(Modifier.PUBLIC)
            ) {
                funList.add(it)
            }
        }
        return funList
    }

    private fun getParamsName(variableElements: MutableList<ParameterSpec>): String {
        val paramsName = StringBuilder()
        for ((index, element) in variableElements.withIndex()) {
            if (index > 0) paramsName.append(", ")
            paramsName.append(element.name)
        }
        return paramsName.toString()
    }

    //????????????????????? ??????:<T> ???<K,V>??????
    private fun getTypeVariableString(typeVariableNames: ArrayList<TypeVariableName>, mirror: TypeMirror): String {
        val name = mirror.toString()
        val simpleName = name.substring(name.lastIndexOf(".") + 1)

        val type = StringBuilder()
        val size = typeVariableNames.size
        for (i in typeVariableNames.indices) {
            if (i == 0) type.append("<")
            type.append("$simpleName<")
            type.append("${typeVariableNames[i].name}>")
            type.append(if (i < size - 1) "," else ">")
        }
        return type.toString()
    }

    //????????????????????? ??????:<T> ???<K,V>??????
    private fun getTypeVariableString(typeVariableNames: ArrayList<TypeVariableName>): String {
        val type = StringBuilder()
        val size = typeVariableNames.size
        for (i in typeVariableNames.indices) {
            if (i == 0) type.append("<")
            type.append(typeVariableNames[i].name)
            type.append(if (i < size - 1) "," else ">")
        }
        return type.toString()
    }


    //??????onParser??????????????????
    private fun getOnParserFunReturnType(typeElement: TypeElement): TypeMirror? {
        typeElement.enclosedElements.forEach {
            if (it is ExecutableElement   //?????????
                && it.getModifiers().contains(Modifier.PUBLIC)  //public??????
                && !it.getModifiers().contains(Modifier.STATIC) //?????????
                && it.simpleName.toString() == "onParse"  //onParse??????
                && it.parameters.size == 1  //??????????????????
                && it.parameters[0].asType().toString() == "okhttp3.Response"  //?????????okhttp3.Response??????
            ) {
                return it.returnType;
            }
        }
        return null
    }
}