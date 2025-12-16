package template;

public abstract class AbstractViewTemplate {
    public final void render() {
        loadData();
        buildUI();
    }
    protected abstract void loadData();
    protected abstract void buildUI();
}
