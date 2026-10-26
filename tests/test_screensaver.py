"""Widget screensaver selection and rollback against the pretend TV."""
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
DREAM = "tv.dumbtv.home/.Screensaver"
OLD = "com.amazon.ftv.screensaver/.app.services.ScreensaverService"


class ScreensaverTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        directory = Path(self.tmp.name)
        self.state = directory / "state.json"
        self.log = directory / "commands.log"
        self.log.touch()
        self.write({"packages": {"tv.dumbtv.home": {"enabled": True, "system": False}},
                    "settings": {"secure/screensaver_components": OLD, "secure/screensaver_enabled": "0"}})
        self.env = dict(os.environ, ADB=str(ROOT / "tests/fake_tv.py"), ADB_SERIAL="test-tv",
                        FAKE_TV_STATE=str(self.state), FAKE_TV_LOG=str(self.log))

    def read(self):
        return json.loads(self.state.read_text())

    def write(self, state):
        self.state.write_text(json.dumps(state))

    def run_screensaver(self, *args, succeeds=True):
        result = subprocess.run(["bash", str(ROOT / "launcher/screensaver.sh"), *args],
                                env=self.env, capture_output=True, text=True, timeout=20)
        self.assertEqual(result.returncode == 0, succeeds, result.stdout + result.stderr)
        return self.read()["settings"]

    def test_select_and_restore_original_component_and_disabled_flag(self):
        settings = self.run_screensaver()
        self.assertEqual(settings["secure/screensaver_components"], DREAM)
        self.assertEqual(settings["secure/screensaver_enabled"], "1")
        self.assertEqual(settings["secure/dumbtv_before_screensaver_components"], OLD)
        settings = self.run_screensaver("--undo")
        self.assertEqual(settings, {"secure/screensaver_components": OLD, "secure/screensaver_enabled": "0"})

    def test_selecting_twice_keeps_the_first_backup(self):
        self.run_screensaver()
        self.run_screensaver()
        settings = self.run_screensaver("--undo")
        self.assertEqual(settings["secure/screensaver_components"], OLD)
        self.assertEqual(settings["secure/screensaver_enabled"], "0")

    def test_unset_settings_are_removed_on_undo(self):
        state = self.read()
        state["settings"] = {}
        self.write(state)
        self.run_screensaver()
        self.assertEqual(self.run_screensaver("--undo"), {})

    def test_later_owner_selection_is_kept(self):
        self.run_screensaver()
        state = self.read()
        state["settings"]["secure/screensaver_components"] = "example.dream/.Photos"
        self.write(state)
        settings = self.run_screensaver("--undo")
        self.assertEqual(settings["secure/screensaver_components"], "example.dream/.Photos")
        self.assertFalse(any("dumbtv_" in key for key in settings))

    def test_later_owner_disable_is_kept(self):
        self.run_screensaver()
        state = self.read()
        state["settings"]["secure/screensaver_enabled"] = "0"
        self.write(state)
        settings = self.run_screensaver("--undo")
        self.assertEqual(settings["secure/screensaver_enabled"], "0")
        self.assertEqual(settings["secure/screensaver_components"], OLD)

    def test_old_launcher_without_a_dream_service_changes_nothing(self):
        state = self.read()
        state["widget_dream"] = False
        self.write(state)
        self.run_screensaver(succeeds=False)
        self.assertEqual(self.read(), state)

    def test_dry_run_changes_nothing(self):
        state = self.read()
        self.run_screensaver("--dry-run")
        self.run_screensaver("--undo", "--dry-run")
        self.assertEqual(self.read(), state)

    def test_undo_without_a_saved_selection_changes_nothing(self):
        state = self.read()
        self.run_screensaver("--undo")
        self.assertEqual(self.read(), state)


if __name__ == "__main__":
    unittest.main()
