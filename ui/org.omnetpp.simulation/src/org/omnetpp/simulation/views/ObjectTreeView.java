package org.omnetpp.simulation.views;

import java.util.Arrays;

import org.apache.commons.lang.ArrayUtils;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.viewers.DecoratingStyledCellLabelProvider;
import org.eclipse.jface.viewers.DelegatingStyledCellLabelProvider.IStyledLabelProvider;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.jface.viewers.StyledString.Styler;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.TextStyle;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IPartListener;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchPartSite;
import org.eclipse.ui.PlatformUI;
import org.omnetpp.common.color.ColorFactory;
import org.omnetpp.common.ui.SelectionProvider;
import org.omnetpp.common.ui.ViewWithMessagePart;
import org.omnetpp.common.util.DisplayUtils;
import org.omnetpp.simulation.SimulationPlugin;
import org.omnetpp.simulation.SimulationUIConstants;
import org.omnetpp.simulation.controller.ISimulationStateListener;
import org.omnetpp.simulation.controller.SimObject;
import org.omnetpp.simulation.controller.SimObjectRef;
import org.omnetpp.simulation.controller.SimulationController;
import org.omnetpp.simulation.controller.SimulationController.SimState;
import org.omnetpp.simulation.editors.SimulationEditor;

/**
 *
 * @author Andras
 */
//FIXME display message if no active simulation
//FIXME better loading of objects!! (from bg, etc)
public class ObjectTreeView extends ViewWithMessagePart {
	public static final String ID = "org.omnetpp.simulation.views.ObjectTreeView";

	protected TreeViewer viewer;
    protected MenuManager contextMenuManager = new MenuManager("#PopupMenu");

    private SimulationEditor associatedSimulationEditor;
    private IPartListener partListener;

    private ISimulationStateListener simulationListener;

	class ViewContentProvider implements ITreeContentProvider {
	    public Object[] getChildren(Object element) {
	        if (element instanceof SimObjectRef) {
	            SimObject simObject = ((SimObjectRef)element).get();
	            if (simObject == null)
	                return new Object[0]; 
                long[] childObjectIds = simObject.childObjectIds;
                ((SimObjectRef)element).controller.loadObjects(Arrays.asList(ArrayUtils.toObject(childObjectIds))); //XXX nice casts!!!
	            return SimObjectRef.wrap(childObjectIds, ((SimObjectRef)element).controller);
	        }
	        return new Object[0];
	    }

	    public Object[] getElements(Object inputElement) {
	        return getChildren(inputElement);
	    }

	    public Object getParent(Object element) {
            if (element instanceof SimObjectRef) {
                SimObject simObject = ((SimObjectRef)element).get();
                if (simObject == null)
                    return null; 
                long owner = simObject.ownerId;
                return new SimObjectRef(owner, ((SimObjectRef)element).controller);
            }
            return null;
	    }

	    public boolean hasChildren(Object element) {
            if (element instanceof SimObjectRef) {
                SimObject simObject = ((SimObjectRef)element).get();
                if (simObject == null)
                    return false; 
                return simObject.childObjectIds.length > 0;
            }
            return false;
	    }

	    public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
	        // Do nothing
	    }

	    public void dispose() {
            // Do nothing
        }
	}

    class ViewLabelProvider implements IStyledLabelProvider {
        private class ColorStyler extends Styler {
            Color color;
            public ColorStyler(Color color) { this.color = color; }
            @Override public void applyStyles(TextStyle textStyle) { textStyle.foreground = color; }
        };

        private Styler greyStyle = new ColorStyler(ColorFactory.GREY60);
        private Styler brownStyle = new ColorStyler(ColorFactory.BURLYWOOD4);

        public StyledString getStyledText(Object element) {
            if (element instanceof SimObjectRef) {
                SimObject obj = ((SimObjectRef)element).get();
                StyledString styledString = new StyledString(obj.fullName);
                styledString.append(" (" + obj.className + ")", greyStyle); //TODO use Simkernel.getObjectShortTypeName(obj);
                styledString.append("  " + obj.info, brownStyle);
                return styledString;
            } 
            return new StyledString(element.toString());
        }

        public Image getImage(Object element) {
            if (element instanceof SimObjectRef) {
                SimObject obj = ((SimObjectRef)element).get();
                String icon = obj.icon; // note: may be empty
                return SimulationPlugin.getCachedImage(SimulationUIConstants.IMG_OBJ_DIR + icon + ".png", SimulationUIConstants.IMG_OBJ_COGWHEEL);
            }
            return PlatformUI.getWorkbench().getSharedImages().getImage(ISharedImages.IMG_OBJS_ERROR_TSK);
        }

        public boolean isLabelProperty(Object element, String property) {
            return true;
        }

        public void dispose() {
            // nothing
        }

        public void addListener(ILabelProviderListener listener) {
            // nothing
        }

        public void removeListener(ILabelProviderListener listener) {
            // nothing
        }
    }

	/**
	 * This is a callback that will allow us to create the viewer and initialize it.
	 */
	public Control createViewControl(Composite parent) {
		viewer = new TreeViewer(parent, SWT.DOUBLE_BUFFERED | SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL);
		viewer.setContentProvider(new ViewContentProvider());
		viewer.setLabelProvider(new DecoratingStyledCellLabelProvider(new ViewLabelProvider(), null, null));

        // create context menu
        getViewSite().registerContextMenu(contextMenuManager, viewer);
        viewer.getTree().setMenu(contextMenuManager.createContextMenu(viewer.getTree()));
        //TODO dynamic menu based on which object is selected

        // double-click opens an inspector
        viewer.addDoubleClickListener(new IDoubleClickListener() {
            public void doubleClick(DoubleClickEvent event) {
                Object element = ((IStructuredSelection)event.getSelection()).getFirstElement();
                associatedSimulationEditor.openInspector(element);
            }
        });

        // export our selection to the workbench
		getViewSite().setSelectionProvider(new SelectionProvider());
        viewer.addSelectionChangedListener(new ISelectionChangedListener() {
			public void selectionChanged(SelectionChangedEvent event) {
				getViewSite().getSelectionProvider().setSelection(event.getSelection());
			}
        });

        // listen on editor changes
        partListener = new IPartListener() {
            public void partActivated(IWorkbenchPart part) {
                if (part instanceof IEditorPart)
                    editorActivated((IEditorPart) part);
            }

            public void partBroughtToTop(IWorkbenchPart part) {
            }

            public void partClosed(IWorkbenchPart part) {
                if (part instanceof IEditorPart)
                    editorClosed((IEditorPart) part);
            }

            public void partDeactivated(IWorkbenchPart part) {
            }

            public void partOpened(IWorkbenchPart part) {
            }
        };
        getSite().getPage().addPartListener(partListener);  //TODO unhookListeners() etc -- see PinnableView!
        
        simulationListener = new ISimulationStateListener() {
            @Override
            public void simulationStateChanged(final SimulationController controller) {
                DisplayUtils.runNowOrAsyncInUIThread(new Runnable() {
                    @Override
                    public void run() {
                        ObjectTreeView.this.simulationStateChanged(controller);
                    }
                });
            }
        };
        
        // associate ourselves with the current simulation editor
        Display.getCurrent().asyncExec(new Runnable() {
            @Override
            public void run() {
                SimulationEditor editor = getActiveSimulationEditor();
                if (editor != null)
                    associateWithEditor(editor);
                else
                    showMessage("No associated simulation.");
            }
        });
        
        return viewer.getTree();
	}

	protected void simulationStateChanged(SimulationController controller) {
        if (controller.getState() == SimState.DISCONNECTED)
            showMessage("No simulation process.");
        else {
            hideMessage();
            viewer.refresh();
        }
    }

    protected void editorActivated(IEditorPart editor) {
	    if (editor != associatedSimulationEditor && editor instanceof SimulationEditor)
	        associateWithEditor((SimulationEditor)editor);
	}

	protected void editorClosed(IEditorPart editor) {
	    if (editor == associatedSimulationEditor)
	        disassociateFromEditor();
	}

    protected void associateWithEditor(SimulationEditor editor) {
        associatedSimulationEditor = editor;

        SimulationController controller = ((SimulationEditor)editor).getSimulationController();
        controller.addSimulationStateListener(simulationListener);

        if (controller.getState() == SimState.DISCONNECTED) {
            showMessage("No simulation process.");
        }
        else {
            hideMessage();
            long simulationObjectId = controller.getRootObjectId("simulation");
            viewer.setInput(new SimObjectRef(simulationObjectId, controller));
            viewer.refresh();
        }
    }

    protected void disassociateFromEditor() {
        associatedSimulationEditor.getSimulationController().removeSimulationStateListener(simulationListener);
        associatedSimulationEditor = null;
        viewer.setInput(null);
        viewer.refresh();

        showMessage("No associated simulation.");
    }

    protected SimulationEditor getActiveSimulationEditor() {
	    IWorkbenchPartSite site = getSite();
	    IWorkbenchPage page = site==null ? null : site.getWorkbenchWindow().getActivePage();
	    if (page != null) {
	        if (page.getActiveEditor() instanceof SimulationEditor)
	            return (SimulationEditor)page.getActiveEditor();
	        // no active simulation editor; just return the first one we find
	        for (IEditorReference ref : page.getEditorReferences())
	            if (ref.getEditor(false) instanceof SimulationEditor)
	                return (SimulationEditor)ref.getEditor(false);
	    }
	    return null;
	}

    /**
	 * Passing the focus request to the viewer's control.
	 */
	public void setFocus() {
		viewer.getControl().setFocus();
	}

    @Override
    public void dispose() {
        if (associatedSimulationEditor != null)
            associatedSimulationEditor.getSimulationController().removeSimulationStateListener(simulationListener);
        super.dispose();
    }
}