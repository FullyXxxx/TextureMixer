package com.fullyx.texturemixer.tooling;

import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.format.MappingFormat;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import net.fabricmc.mappingio.tree.MappingTreeView.ClassMappingView;
import net.fabricmc.mappingio.tree.MappingTreeView.ElementMappingView;
import net.fabricmc.mappingio.tree.MappingTreeView.MemberMappingView;
import net.fabricmc.tinyremapper.IMappingProvider;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.zip.ZipFile;

/** Composes the pinned Legacy Yarn and MCP mappings through Mojang's names. */
public final class ForgeRemap {
    private final MemoryMappingTree intermediary;
    private final MemoryMappingTree yarn;
    private final MemoryMappingTree srg;

    private ForgeRemap(Path intermediary, Path yarn, Path srg) throws IOException {
        this.intermediary = read(intermediary, "mappings/mappings.tiny", MappingFormat.TINY_2_FILE);
        this.yarn = read(yarn, "mappings/mappings.tiny", MappingFormat.TINY_2_FILE);
        this.srg = read(srg, "joined.srg", MappingFormat.SRG_FILE);
    }

    private static MemoryMappingTree read(Path archive, String member, MappingFormat format) throws IOException {
        MemoryMappingTree tree = new MemoryMappingTree();
        try (ZipFile zip = new ZipFile(archive.toFile());
             InputStreamReader reader = new InputStreamReader(zip.getInputStream(zip.getEntry(member)), StandardCharsets.UTF_8)) {
            MappingReader.read(reader, format, tree);
        }
        return tree;
    }

    private void mappings(IMappingProvider.MappingAcceptor out) {
        for (ClassMappingView srgClass : srg.getClasses()) {
            String officialOwner = srgClass.getSrcName();
            ClassMappingView intermediate = intermediary.getClass(officialOwner);
            String intermediateOwner = name(intermediate, officialOwner);
            ClassMappingView yarnClass = yarn.getClass(intermediateOwner);
            String owner = name(yarnClass, intermediateOwner);
            out.acceptClass(owner, srgClass.getDstName(0));

            for (MemberMappingView method : srgClass.getMethods()) {
                String from = method.getSrcName();
                String desc = method.getSrcDesc();
                MemberMappingView i = intermediate == null ? null : intermediate.getMethod(from, desc);
                String intermediateName = name(i, from);
                String intermediateDesc = intermediary.mapDesc(desc, 0);
                MemberMappingView named = yarnClass == null ? null : yarnClass.getMethod(intermediateName, intermediateDesc);
                out.acceptMethod(new IMappingProvider.Member(owner, name(named, intermediateName),
                        yarn.mapDesc(intermediateDesc, 0)), method.getDstName(0));
            }
            // SRG field entries omit descriptors. The intermediary table retains them.
            if (intermediate == null) continue;
            for (MemberMappingView field : intermediate.getFields()) {
                MemberMappingView mapped = srgClass.getField(field.getSrcName(), null);
                if (mapped == null) continue;
                String from = name(field, field.getSrcName());
                String desc = intermediary.mapDesc(field.getSrcDesc(), 0);
                MemberMappingView named = yarnClass == null ? null : yarnClass.getField(from, desc);
                out.acceptField(new IMappingProvider.Member(owner, name(named, from),
                        yarn.mapDesc(desc, 0)), mapped.getDstName(0));
            }
        }
    }

    private static String name(ElementMappingView entry, String fallback) {
        return entry == null || entry.getDstName(0) == null ? fallback : entry.getDstName(0);
    }

    public static void main(String[] args) throws IOException {
        ForgeRemap mappings = new ForgeRemap(Paths.get(args[0]), Paths.get(args[1]), Paths.get(args[2]));
        Path input = Paths.get(args[3]);
        Path output = Paths.get(args[4]);
        Path[] classpath = Arrays.stream(args).skip(5).map(Paths::get).toArray(Path[]::new);
        Files.createDirectories(output.getParent());
        Files.deleteIfExists(output);
        TinyRemapper remapper = TinyRemapper.newRemapper().withMappings(mappings::mappings).build();
        try (OutputConsumerPath consumer = new OutputConsumerPath.Builder(output).build()) {
            consumer.addNonClassFiles(input);
            remapper.readInputs(input);
            remapper.readClassPath(classpath);
            remapper.apply(consumer);
        } finally {
            remapper.finish();
        }
    }
}
