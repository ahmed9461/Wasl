from pathlib import Path

path = Path("app/src/main/java/com/wasl/app/data/RepositoryModels.kt")
text = path.read_text(encoding="utf-8")
old = """    val personName: String,\n    val personPhone: String? = null,\n    val personEmail: String? = null,\n    val direction: DebtDirection,\n    val originalAmount: Money,\n    val openedAt: Instant,\n    val createdAt: Instant,\n"""
new = """    val personName: String,\n    val direction: DebtDirection,\n    val originalAmount: Money,\n    val openedAt: Instant,\n    val createdAt: Instant,\n    val personPhone: String? = null,\n    val personEmail: String? = null,\n"""
if text.count(old) != 1:
    raise SystemExit(f"expected one CreatePersonWithDebtCommand field block, found {text.count(old)}")
path.write_text(text.replace(old, new, 1), encoding="utf-8")
print("contact command parameter order fixed")
