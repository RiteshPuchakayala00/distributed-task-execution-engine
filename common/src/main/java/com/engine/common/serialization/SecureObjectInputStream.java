package com.engine.common.serialization;

import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.util.Set;

/**
 * A secure ObjectInputStream that restricts deserialization to a whitelist of
 * allowed classes and packages to prevent gadget chain attacks.
 *
 * @author Engine Team
 */
public class SecureObjectInputStream extends ObjectInputStream {

    // Whitelist of allowed packages/classes
    private static final Set<String> ALLOWED_PACKAGES = Set.of(
            "java.lang.",
            "java.util.",
            "com.engine.common."
    );

    private static final Set<String> ALLOWED_EXACT = Set.of(
            "java.time.Instant" // If we need specific java.time classes
    );

    public SecureObjectInputStream(InputStream in) throws IOException {
        super(in);
    }

    @Override
    protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
        String name = desc.getName();
        
        // Check exact match
        if (ALLOWED_EXACT.contains(name)) {
            return super.resolveClass(desc);
        }

        // Check array types (e.g., [B for byte[], [Ljava.lang.String;)
        if (name.startsWith("[")) {
            // Arrays are generally safe if their component types are safe,
            // but for strictness we could parse the component type.
            // For now, allow primitive arrays and basic object arrays.
            if (name.length() == 2) {
                return super.resolveClass(desc); // primitive array
            }
        }

        // Check package whitelist
        for (String pkg : ALLOWED_PACKAGES) {
            if (name.startsWith(pkg)) {
                return super.resolveClass(desc);
            }
        }

        throw new InvalidClassException("Unauthorized deserialization attempt for class: " + name);
    }
}
