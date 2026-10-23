import importlib.util,subprocess,tempfile,unittest
from pathlib import Path
from unittest.mock import patch
spec=importlib.util.spec_from_file_location("receiver_updates",Path(__file__).with_name("receiver_updates.py"))
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
class ReceiverUpdateTests(unittest.TestCase):
    def test_latest_version_uses_semantic_numbers_and_ignores_prereleases(self):
        lines="a refs/tags/v2.0.9\nb refs/tags/v2.0.10\nc refs/tags/v3.0.0-beta\n"
        with patch.object(m.subprocess,"check_output",return_value=lines):self.assertEqual(m.latest_tag("example"),"v2.0.10")
    def test_existing_destination_and_option_injection_are_refused(self):
        with tempfile.TemporaryDirectory() as d, patch.object(m,"run") as run:
            with self.assertRaises(ValueError):m.prepare({},"v1.0.0",d)
            with self.assertRaises(ValueError):m.prepare({},"--upload-pack=bad",Path(d)/"new")
            run.assert_not_called()
    def test_patch_conflict_never_runs_validation_or_installs(self):
        calls=[]
        def fake(command,**kw):
            calls.append(command)
            if command[:2]==["git","apply"]:raise subprocess.CalledProcessError(1,command)
        with tempfile.TemporaryDirectory() as d, patch.object(m,"run",side_effect=fake):
            with self.assertRaises(subprocess.CalledProcessError):m.prepare({"url":"example","patch":"example.patch","tests":[["validate"]]},"v1.0.0",Path(d)/"new",True)
        self.assertEqual(calls[-1][:2],["git","apply"])
        self.assertFalse(any(c==["validate"] for c in calls))
if __name__=="__main__":unittest.main()
