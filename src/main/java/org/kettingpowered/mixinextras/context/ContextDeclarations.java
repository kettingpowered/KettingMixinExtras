package org.kettingpowered.mixinextras.context;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.spongepowered.asm.util.Annotations;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

final class ContextDeclarations {

    private final Map<String, Map<String, List<AnnotationNode>>> sources = new HashMap<>();
    private final Map<String, Contract> contracts = new HashMap<>();

    void register(String owner, String mixin, List<AnnotationNode> declarations) {
        if (declarations.isEmpty()) return;

        Map<String, List<AnnotationNode>> existing = sources.computeIfAbsent(owner, key -> new LinkedHashMap<>());
        if (existing.containsKey(mixin)) return;
        if (contracts.values().stream().anyMatch(contract -> contract.owner.equals(owner))) {
            throw new IllegalArgumentException("@ContextOverload declarations arrived after binding " + owner + ": " + mixin);
        }
        existing.put(mixin, List.copyOf(declarations));
    }

    Contract resolve(String owner, String method) {
        String reference = 'L' + owner + ';' + method;
        Contract existing = contracts.get(reference);
        if (existing != null) return existing;

        Map<String, Type> types = new TreeMap<>();
        for (List<AnnotationNode> declarations : sources.getOrDefault(owner, Map.of()).values()) {
            for (AnnotationNode declaration : declarations) {
                if (!method.equals(Annotations.getValue(declaration, "method"))) continue;

                List<AnnotationNode> arguments = Annotations.getValue(declaration, "args");
                if (arguments == null || arguments.isEmpty()) {
                    throw new IllegalArgumentException("@ContextOverload requires added arguments: " + reference);
                }
                for (AnnotationNode argument : arguments) {
                    String name = Annotations.getValue(argument, "name");
                    Type type = Annotations.getValue(argument, "type");
                    if (name == null || name.isBlank() || type == null || type.getSort() == Type.VOID || type.getSort() == Type.METHOD) {
                        throw new IllegalArgumentException("Invalid @ContextOverload argument " + name + " in " + reference);
                    }
                    Type previous = types.putIfAbsent(name, type);
                    if (previous != null && !previous.equals(type)) {
                        throw new IllegalArgumentException("Conflicting @ContextOverload argument " + name + " in " + reference);
                    }
                }
            }
        }
        if (types.isEmpty()) {
            throw new IllegalArgumentException("No @ContextOverload declaration for " + reference);
        }

        Map<String, Parameter> parameters = new LinkedHashMap<>();
        types.forEach((name, type) -> parameters.put(name, new Parameter(type, parameters.size())));
        Contract contract = new Contract(owner, method, Collections.unmodifiableMap(parameters));
        contracts.put(reference, contract);
        return contract;
    }

    record Contract(String owner, String key, Map<String, Parameter> parameters) {
    }

    record Parameter(Type type, int slot) {
    }
}
