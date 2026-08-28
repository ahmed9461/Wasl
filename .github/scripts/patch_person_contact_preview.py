from pathlib import Path

path = Path("app/src/main/java/com/wasl/app/WaslApp.kt")
text = path.read_text(encoding="utf-8")
old = """                onPersonModeChange = {},\n                onPersonNameChange = {},\n                onPeopleQueryChange = {},\n"""
new = """                onPersonModeChange = {},\n                onPersonNameChange = {},\n                onPersonPhoneChange = {},\n                onPersonEmailChange = {},\n                onPersonNotesChange = {},\n                onPeopleQueryChange = {},\n"""
count = text.count(old)
if count != 1:
    raise SystemExit(f"expected one home preview callback block, found {count}")
path.write_text(text.replace(old, new, 1), encoding="utf-8")
print("home preview contact callbacks wired")
