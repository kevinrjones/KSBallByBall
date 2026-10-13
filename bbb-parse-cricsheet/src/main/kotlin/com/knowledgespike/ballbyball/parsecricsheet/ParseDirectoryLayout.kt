package com.knowledgespike.ballbyball.parsecricsheet

import java.nio.file.Files
import java.nio.file.Path

internal data class ParseDirectoryLayout(
    val input: Path,
    val output: Path,
    val notParsed: Path
)

internal fun parseDirectoryLayout(
    inputDirectory: Path,
    outputDirectory: Path,
    notParsedDirectory: Path
): ParseDirectoryLayout {
    val layout = ParseDirectoryLayout(
        input = resolvePhysicalPath(inputDirectory),
        output = resolvePhysicalPath(outputDirectory),
        notParsed = resolvePhysicalPath(notParsedDirectory)
    )
    requireDisjoint("input", layout.input, "output", layout.output)
    requireDisjoint("input", layout.input, "not-parsed", layout.notParsed)
    requireDisjoint("output", layout.output, "not-parsed", layout.notParsed)
    return layout
}

private fun requireDisjoint(firstName: String, first: Path, secondName: String, second: Path) {
    val lexicallyOverlapping = first.startsWith(second) || second.startsWith(first)
    val physicallyEqual = Files.exists(first) && Files.exists(second) && Files.isSameFile(first, second)
    require(!lexicallyOverlapping && !physicallyEqual) {
        "Parse $firstName and $secondName directories must not overlap"
    }
}

internal fun resolvePhysicalPath(path: Path): Path {
    val absolute = path.toAbsolutePath().normalize()
    if (Files.exists(absolute)) return absolute.toRealPath()

    val missingComponents = mutableListOf<Path>()
    var existingParent = absolute
    while (!Files.exists(existingParent)) {
        require(!Files.isSymbolicLink(existingParent)) {
            "Parse directory must not contain a dangling symbolic link: $existingParent"
        }
        missingComponents.add(requireNotNull(existingParent.fileName))
        existingParent = requireNotNull(existingParent.parent)
    }
    return missingComponents.asReversed().fold(existingParent.toRealPath()) { parent, component ->
        parent.resolve(component)
    }
}