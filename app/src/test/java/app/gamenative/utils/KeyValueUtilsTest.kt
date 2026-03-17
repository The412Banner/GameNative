package app.gamenative.utils

import app.gamenative.enums.PathType
import `in`.dragonbra.javasteam.types.KeyValue
import org.junit.Assert.assertEquals
import org.junit.Test

class KeyValueUtilsTest {

    /**
     * Blue Revolver (App ID 439490) has a Windows rootoverride that remaps `gameinstall`
     * to `WinAppDataRoaming` with an empty addpath. All three savefiles use `gameinstall`
     * as root, so they should all be remapped; paths should be unchanged.
     */
    @Test
    fun blueRevolverWindowsRootOverrideRemapsGameInstallToWinAppDataRoaming() {
        val kvString = """
            "appinfo"
            {
                "appid"     "439490"
                "ufs"
                {
                    "quota"         "1000000000"
                    "maxnumfiles"   "1000"
                    "savefiles"
                    {
                        "1"
                        {
                            "root"      "gameinstall"
                            "path"      "blue-revolver-final"
                            "pattern"   "save2.lua"
                        }
                        "2"
                        {
                            "root"      "gameinstall"
                            "path"      "blue-revolver-double-action"
                            "pattern"   "brda_save.sav"
                        }
                        "3"
                        {
                            "root"      "gameinstall"
                            "path"      "love/blue-revolver-final"
                            "pattern"   "save2.lua"
                            "platforms"
                            {
                                "1"     "Linux"
                            }
                        }
                    }
                    "rootoverrides"
                    {
                        "0"
                        {
                            "root"          "gameinstall"
                            "os"            "Windows"
                            "oscompare"     "="
                            "useinstead"    "WinAppDataRoaming"
                            "addpath"       ""
                        }
                        "1"
                        {
                            "root"          "gameinstall"
                            "os"            "MacOS"
                            "oscompare"     "="
                            "useinstead"    "MacAppSupport"
                            "addpath"       ""
                        }
                        "2"
                        {
                            "root"          "gameinstall"
                            "os"            "Linux"
                            "oscompare"     "="
                            "useinstead"    "LinuxHome"
                            "addpath"       ".local/share/"
                        }
                    }
                }
            }
        """.trimIndent()
        val kv = KeyValue.loadFromString(kvString)!!
        val steamApp = kv.generateSteamApp()

        val patterns = steamApp.ufs.saveFilePatterns
        assertEquals(2, patterns.size)

        assertEquals(PathType.WinAppDataRoaming, patterns[0].root)
        assertEquals("blue-revolver-final", patterns[0].path)
        assertEquals("save2.lua", patterns[0].pattern)
        assertEquals(0, patterns[0].recursive)

        assertEquals(PathType.WinAppDataRoaming, patterns[1].root)
        assertEquals("blue-revolver-double-action", patterns[1].path)
        assertEquals("brda_save.sav", patterns[1].pattern)
        assertEquals(0, patterns[1].recursive)
    }

    /**
     * A Windows rootoverride with a non-empty addpath should prepend the addpath to the original
     * save file path (e.g. addpath="AppData/Roaming" + path="MyGame" → "AppData/Roaming/MyGame").
     */
    @Test
    fun windowsRootOverrideWithNonEmptyAddPathPrependsToSavePath() {
        val kvString = """
            "appinfo"
            {
                "appid"     "123456"
                "ufs"
                {
                    "quota"         "1000000000"
                    "maxnumfiles"   "10"
                    "savefiles"
                    {
                        "0"
                        {
                            "root"      "gameinstall"
                            "path"      "saves"
                            "pattern"   "*.sav"
                        }
                    }
                    "rootoverrides"
                    {
                        "0"
                        {
                            "root"          "gameinstall"
                            "os"            "Windows"
                            "oscompare"     "="
                            "useinstead"    "WinAppDataRoaming"
                            "addpath"       "MyGame"
                        }
                    }
                }
            }
        """.trimIndent()
        val kv = KeyValue.loadFromString(kvString)!!
        val steamApp = kv.generateSteamApp()

        val patterns = steamApp.ufs.saveFilePatterns
        assertEquals(1, patterns.size)
        assertEquals(PathType.WinAppDataRoaming, patterns[0].root)
        assertEquals("MyGame/saves", patterns[0].path)
        assertEquals("*.sav", patterns[0].pattern)
        assertEquals(0, patterns[0].recursive)
    }

    /**
     * A Windows rootoverride whose addpath has a trailing slash should not produce a double
     * separator (e.g. "MyGame/" + "saves" → "MyGame/saves", not "MyGame//saves").
     */
    @Test
    fun windowsRootOverrideWithTrailingSlashAddPathDoesNotDuplicateSeparator() {
        val kvString = """
            "appinfo"
            {
                "appid"     "123457"
                "ufs"
                {
                    "quota"         "1000000000"
                    "maxnumfiles"   "10"
                    "savefiles"
                    {
                        "0"
                        {
                            "root"      "gameinstall"
                            "path"      "saves"
                            "pattern"   "*.sav"
                        }
                    }
                    "rootoverrides"
                    {
                        "0"
                        {
                            "root"          "gameinstall"
                            "os"            "Windows"
                            "oscompare"     "="
                            "useinstead"    "WinAppDataRoaming"
                            "addpath"       "MyGame/"
                        }
                    }
                }
            }
        """.trimIndent()
        val kv = KeyValue.loadFromString(kvString)!!
        val steamApp = kv.generateSteamApp()

        val patterns = steamApp.ufs.saveFilePatterns
        assertEquals(1, patterns.size)
        assertEquals(PathType.WinAppDataRoaming, patterns[0].root)
        assertEquals("MyGame/saves", patterns[0].path)
        assertEquals("*.sav", patterns[0].pattern)
        assertEquals(0, patterns[0].recursive)
    }

    /**
     * A savefile restricted to Linux via a `platforms` block should be excluded on Windows.
     * A savefile with no `platforms` block should always be included.
     */
    @Test
    fun saveFileWithNonWindowsPlatformIsExcluded() {
        val kvString = """
            "appinfo"
            {
                "appid"     "111111"
                "ufs"
                {
                    "quota"         "1000000000"
                    "maxnumfiles"   "10"
                    "savefiles"
                    {
                        "0"
                        {
                            "root"      "WinMyDocuments"
                            "path"      "saves"
                            "pattern"   "*.sav"
                            "platforms"
                            {
                                "1"     "Linux"
                            }
                        }
                        "1"
                        {
                            "root"      "WinMyDocuments"
                            "path"      "saves"
                            "pattern"   "*.bak"
                        }
                    }
                }
            }
        """.trimIndent()
        val kv = KeyValue.loadFromString(kvString)!!
        val steamApp = kv.generateSteamApp()

        val patterns = steamApp.ufs.saveFilePatterns
        assertEquals(1, patterns.size)
        assertEquals(PathType.WinMyDocuments, patterns[0].root)
        assertEquals("*.bak", patterns[0].pattern)
    }

    /**
     * Savefiles with an explicit `platforms { "Windows" }` block (Noita-style) should be included.
     */
    @Test
    fun noitaExplicitWindowsPlatformIsIncluded() {
        val kvString = """
            "appinfo"
            {
                "appid"     "881100"
                "ufs"
                {
                    "quota"         "1000000000"
                    "maxnumfiles"   "10"
                    "savefiles"
                    {
                        "0"
                        {
                            "root"      "WinAppDataLocalLow"
                            "path"      "Nolla_Games_Noita"
                            "pattern"   "save00/world/*.bin"
                            "recursive" "1"
                            "platforms"
                            {
                                "1"     "Windows"
                            }
                        }
                        "1"
                        {
                            "root"      "WinAppDataLocalLow"
                            "path"      "Nolla_Games_Noita"
                            "pattern"   "save00/*.xml"
                            "recursive" "0"
                            "platforms"
                            {
                                "1"     "Windows"
                            }
                        }
                    }
                }
            }
        """.trimIndent()
        val kv = KeyValue.loadFromString(kvString)!!
        val steamApp = kv.generateSteamApp()

        val patterns = steamApp.ufs.saveFilePatterns
        assertEquals(2, patterns.size)
        assertEquals(PathType.WinAppDataLocalLow, patterns[0].root)
        assertEquals("Nolla_Games_Noita", patterns[0].path)
        assertEquals("save00/world/*.bin", patterns[0].pattern)
        assertEquals(1, patterns[0].recursive)
        assertEquals(PathType.WinAppDataLocalLow, patterns[1].root)
        assertEquals("Nolla_Games_Noita", patterns[1].path)
        assertEquals("save00/*.xml", patterns[1].pattern)
        assertEquals(0, patterns[1].recursive)
    }

    /**
     * Cult of the Lamb uses a Windows rootoverride with `pathtransforms` to remap the save
     * path from `saves` to `Massive Monster/Cult Of The Lamb/saves`.
     */
    @Test
    fun cultOfTheLambPathtransformsRemapsPath() {
        val kvString = """
            "appinfo"
            {
                "appid"     "1313140"
                "ufs"
                {
                    "quota"         "1048576000"
                    "maxnumfiles"   "10000"
                    "savefiles"
                    {
                        "0"
                        {
                            "root"      "gameinstall"
                            "path"      "saves"
                            "pattern"   "*.json"
                            "recursive" "1"
                        }
                        "1"
                        {
                            "root"      "gameinstall"
                            "path"      "saves"
                            "pattern"   "*.mp"
                            "recursive" "1"
                        }
                    }
                    "rootoverrides"
                    {
                        "0"
                        {
                            "root"          "gameinstall"
                            "os"            "Windows"
                            "oscompare"     "="
                            "useinstead"    "WinAppDataLocalLow"
                            "pathtransforms"
                            {
                                "0"
                                {
                                    "find"      "saves"
                                    "replace"   "Massive Monster/Cult Of The Lamb/saves"
                                }
                            }
                        }
                        "1"
                        {
                            "root"          "gameinstall"
                            "os"            "MacOS"
                            "oscompare"     "="
                            "useinstead"    "MacAppSupport"
                            "pathtransforms"
                            {
                                "0"
                                {
                                    "find"      "saves"
                                    "replace"   "Massive Monster/Cult Of The Lamb/saves"
                                }
                            }
                        }
                    }
                }
            }
        """.trimIndent()
        val kv = KeyValue.loadFromString(kvString)!!
        val steamApp = kv.generateSteamApp()

        val patterns = steamApp.ufs.saveFilePatterns
        assertEquals(2, patterns.size)

        assertEquals(PathType.WinAppDataLocalLow, patterns[0].root)
        assertEquals("Massive Monster/Cult Of The Lamb/saves", patterns[0].path)
        assertEquals("*.json", patterns[0].pattern)
        assertEquals(1, patterns[0].recursive)

        assertEquals(PathType.WinAppDataLocalLow, patterns[1].root)
        assertEquals("Massive Monster/Cult Of The Lamb/saves", patterns[1].path)
        assertEquals("*.mp", patterns[1].pattern)
        assertEquals(1, patterns[1].recursive)
    }

    /**
     * Hades has only a MacOS rootoverride. Windows save paths should be left untouched.
     */
    @Test
    fun hadesNonWindowsRootOverrideDoesNotAffectSaveFilePaths() {
        val kvString = """
            "appinfo"
            {
                "appid"     "1145360"
                "ufs"
                {
                    "quota"         "62914560"
                    "maxnumfiles"   "20"
                    "savefiles"
                    {
                        "0"
                        {
                            "root"      "WinMyDocuments"
                            "path"      "Saved Games/Hades"
                            "pattern"   "Profile*.sav"
                        }
                    }
                    "rootoverrides"
                    {
                        "1"
                        {
                            "root"          "WinMyDocuments"
                            "os"            "MacOS"
                            "oscompare"     "="
                            "useinstead"    "MacAppSupport"
                        }
                    }
                }
            }
        """.trimIndent()

        val kv = KeyValue.loadFromString(kvString)!!
        val steamApp = kv.generateSteamApp()

        val patterns = steamApp.ufs.saveFilePatterns
        assertEquals(1, patterns.size)
        assertEquals(PathType.WinMyDocuments, patterns[0].root)
        assertEquals("Saved Games/Hades", patterns[0].path)
        assertEquals("Profile*.sav", patterns[0].pattern)
        assertEquals(0, patterns[0].recursive)
    }
}
