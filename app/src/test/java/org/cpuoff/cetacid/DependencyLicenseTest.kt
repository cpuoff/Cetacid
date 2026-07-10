package org.cpuoff.cetacid

import java.io.File
import org.apache.commons.io.FilenameUtils
import org.junit.Test
import org.cpuoff.cetacid.data.listDependencies

class DependencyLicenseTest {
    @Test
    fun noMissingLicenseText() {
        listDependencies { File(FilenameUtils.concat("src/main/assets", it)).readText() }
    }
}
