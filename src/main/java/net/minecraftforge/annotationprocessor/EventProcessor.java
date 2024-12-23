/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.annotationprocessor;

import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.IModBusEvent;
import org.jetbrains.annotations.ApiStatus;

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import java.lang.annotation.Annotation;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

@ApiStatus.Internal
public class EventProcessor extends DefaultProcessor {
    enum Bus {
        FORGE,
        MOD,
        UNKNOWN
    }

    @Override
    protected Set<Class<? extends Annotation>> getSupportedAnnotations() {
        return Set.of(
                Mod.EventBusSubscriber.class
        );
    }

    @Override
    public String getId() {
        return "ForgeEventBusSubscriber";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    @Override
    public boolean processDefault(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        roundEnv.getElementsAnnotatedWith(SubscribeEvent.class)
                .forEach(event -> {
                    if (event.getKind() == ElementKind.METHOD) {
                        var superType = event.getEnclosingElement();
                        var busType = getBus(Mod.EventBusSubscriber.class, superType);
                        var hasModAnnotation = superType.getAnnotation(Mod.class) != null;
                        if (busType == Bus.UNKNOWN) return;

                        if (hasModAnnotation) {
                            if (!event.getModifiers().contains(Modifier.PUBLIC)) {
                                getEnv()
                                        .getMessager()
                                        .printError(
                                                "Listener needs to be public",
                                                event
                                        );
                                return;
                            }
                        } else {
                            if (event.getModifiers().contains(Modifier.STATIC) && event.getModifiers().contains(Modifier.PRIVATE)) {
                                getEnv()
                                        .getMessager()
                                        .printError(
                                                "Listener needs to be public",
                                                event
                                        );
                                return;
                            }
                            if (!event.getModifiers().contains(Modifier.STATIC)) {
                                return;
                            }
                        }

                        if (event instanceof ExecutableElement executableElement) {
                            var params = executableElement.getParameters();
                            if (params.size() != 1) {
                                getEnv()
                                        .getMessager()
                                        .printError(
                                                "Event Listener can only have one Parameter",
                                                event
                                        );
                            } else {
                                var param = params.getFirst();
                                if (!isAssignable(Event.class, param.asType())) {
                                    getEnv()
                                            .getMessager()
                                            .printError(
                                                    "EventType does not extend or inherit %s".formatted(Event.class.getCanonicalName()),
                                                    param
                                            );
                                    return;
                                }

                                var isModEvent = isAssignable(IModBusEvent.class, param.asType());
                                if (isModEvent && busType == Bus.FORGE) {
                                    getEnv()
                                            .getMessager()
                                            .printError(
                                                    "EventType does not belong on the Forge Bus, belongs on ModBus",
                                                    param
                                            );
                                } else if (!isModEvent && busType == Bus.MOD) {
                                    getEnv()
                                            .getMessager()
                                            .printError(
                                                    "EventType does not belong on the Mod Bus, belongs on Forge Bus",
                                                    param
                                            );
                                }
                            }
                        }
                    }
                });

        return true;
    }

    private Bus getBus(Class<? extends Annotation> annotation, Element element) {
        AtomicReference<Bus> result = new AtomicReference<>(Bus.UNKNOWN);
        for (AnnotationMirror annotationMirror : element.getAnnotationMirrors()) {
            if (annotationMirror.getAnnotationType().toString().equals(annotation.getCanonicalName())) {
                result.set(Bus.FORGE);
                annotationMirror.getElementValues().forEach((k, v) -> {
                    if (k.toString().equals("bus()") && v.getValue().toString().equals("MOD")) {
                        result.set(Bus.MOD);
                    }
                });
            }
        }
        return result.get();
    }


    private boolean isAssignable(Class<?> interfaceType, TypeMirror typeMirror) {
        return getEnv().getTypeUtils().isAssignable(
                typeMirror,
                getEnv().getElementUtils().getTypeElement(
                        interfaceType.getCanonicalName()
                ).asType()
        );
    }
}
