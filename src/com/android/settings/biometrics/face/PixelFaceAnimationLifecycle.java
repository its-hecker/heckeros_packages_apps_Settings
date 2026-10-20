/* SPDX-License-Identifier: Apache-2.0 */
package com.android.settings.biometrics.face;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.os.Handler;
import android.util.Log;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/** Stops timers in the small pinned renderer, which predates a public lifecycle API. */
final class PixelFaceAnimationLifecycle {
    private static final String CURVE =
            "com.google.android.settings.biometrics.face.anim.curve.";

    static void stop(Object... roots) {
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        List<Handler> handlers = new ArrayList<>();
        List<Animator> animators = new ArrayList<>();
        for (Object root : roots) collect(root, visited, handlers, animators);
        for (Handler handler : handlers) handler.removeCallbacksAndMessages(null);
        for (Animator animator : animators) {
            animator.removeAllListeners();
            if (animator instanceof ValueAnimator) {
                ((ValueAnimator) animator).removeAllUpdateListeners();
            }
            animator.cancel();
        }
        for (Handler handler : handlers) handler.removeCallbacksAndMessages(null);
    }

    private static void collect(Object value, Set<Object> visited, List<Handler> handlers,
            List<Animator> animators) {
        if (value == null || !visited.add(value)) return;
        if (value instanceof Handler) {
            handlers.add((Handler) value);
            return;
        }
        if (value instanceof Animator) {
            animators.add((Animator) value);
            return;
        }
        Class<?> type = value.getClass();
        if (type.isArray()) {
            if (!type.getComponentType().isPrimitive()) {
                for (int i = 0; i < Array.getLength(value); i++) {
                    collect(Array.get(value, i), visited, handlers, animators);
                }
            }
            return;
        }
        // Never traverse Context, Views, framework objects, or an enrollment owner.
        if (!type.getName().startsWith(CURVE)) return;
        for (Field field : type.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) continue;
            try {
                field.setAccessible(true);
                collect(field.get(value), visited, handlers, animators);
            } catch (ReflectiveOperationException | SecurityException error) {
                Log.w("PixelFaceAnimation", "Unable to stop renderer field " + field, error);
            }
        }
    }
}
