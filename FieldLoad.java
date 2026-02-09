import soot.Local;
import soot.SootField;

public class FieldLoad {
    public final Local base;
    public final SootField field;
    public final Local value;

    public FieldLoad(Local base, SootField field, Local value) {
        this.base = base;
        this.field = field;
        this.value = value;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof FieldLoad)) return false;
        FieldLoad other = (FieldLoad) o;
        return base.equals(other.base)
            && field.equals(other.field)
            && value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return base.hashCode() * 31 * 31
             + field.hashCode() * 31
             + value.hashCode();
    }

    @Override
    public String toString() {
        return value + " = " + base + "." + field.getName();
    }
}
