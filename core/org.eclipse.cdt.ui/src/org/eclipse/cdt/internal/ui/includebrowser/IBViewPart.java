/*******************************************************************************
 * Copyright (c) 2006 Wind River Systems, Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Markus Schorn - initial API and implementation
 *******************************************************************************/ 

package org.eclipse.cdt.internal.ui.includebrowser;

import java.util.ArrayList;
import java.util.Arrays;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.util.LocalSelectionTransfer;
import org.eclipse.jface.viewers.IOpenListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.OpenEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.search.ui.IContextMenuConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DropTarget;
import org.eclipse.swt.dnd.FileTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.IPageLayout;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPartSite;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.actions.ContributionItemFactory;
import org.eclipse.ui.actions.OpenFileAction;
import org.eclipse.ui.part.IShowInSource;
import org.eclipse.ui.part.IShowInTarget;
import org.eclipse.ui.part.IShowInTargetList;
import org.eclipse.ui.part.PageBook;
import org.eclipse.ui.part.ResourceTransfer;
import org.eclipse.ui.part.ShowInContext;
import org.eclipse.ui.part.ViewPart;

import org.eclipse.cdt.core.CCorePlugin;
import org.eclipse.cdt.core.model.CModelException;
import org.eclipse.cdt.core.model.CoreModel;
import org.eclipse.cdt.core.model.ICElement;
import org.eclipse.cdt.core.model.ICProject;
import org.eclipse.cdt.core.model.ITranslationUnit;
import org.eclipse.cdt.ui.CUIPlugin;

import org.eclipse.cdt.internal.ui.CPluginImages;
import org.eclipse.cdt.internal.ui.util.Messages;
import org.eclipse.cdt.internal.ui.viewsupport.EditorOpener;
import org.eclipse.cdt.internal.ui.viewsupport.ExtendedTreeViewer;
import org.eclipse.cdt.internal.ui.viewsupport.TreeNavigator;
import org.eclipse.cdt.internal.ui.viewsupport.WorkingSetFilterUI;

/**
 * The view part for the include browser.
 */
public class IBViewPart extends ViewPart 
        implements IShowInSource, IShowInTarget, IShowInTargetList {

	private static final int MAX_HISTORY_SIZE = 10;
    private static final String TRUE = String.valueOf(true);
    private static final String KEY_WORKING_SET_FILTER = "workingSetFilter"; //$NON-NLS-1$
    private static final String KEY_FILTER_SYSTEM = "systemFilter"; //$NON-NLS-1$
//    private static final String KEY_FILTER_INACTIVE = "inactiveFilter"; //$NON-NLS-1$
    private static final String KEY_INPUT_PATH= "inputPath"; //$NON-NLS-1$
    
    private IMemento fMemento;
    private boolean fShowsMessage;
    private IBNode fLastNavigationNode;
	private ArrayList fHistoryEntries= new ArrayList(MAX_HISTORY_SIZE);

    // widgets
    private PageBook fPagebook;
    private Composite fViewerPage;
    private Composite fInfoPage;
    private Text fInfoText;

    // treeviewer
    private IBContentProvider fContentProvider;
    private IBLabelProvider fLabelProvider;
    private ExtendedTreeViewer fTreeViewer;

    // filters, sorter
    private IBWorkingSetFilter fWorkingSetFilter;
//    private ViewerFilter fInactiveFilter;
    private ViewerFilter fSystemFilter;
    private ViewerComparator fSorterAlphaNumeric;
    private ViewerComparator fSorterReferencePosition;

    // actions
    private Action fIncludedByAction;
    private Action fIncludesToAction;
//    private Action fFilterInactiveAction;
    private Action fFilterSystemAction;
    private Action fShowFolderInLabelsAction;
    private Action fNextAction;
    private Action fPreviousAction;
    private Action fRefreshAction;
	private Action fHistoryAction;

    
    public void setFocus() {
        fPagebook.setFocus();
    }

    public void setMessage(String msg) {
        fInfoText.setText(msg);
        fPagebook.showPage(fInfoPage);
        fShowsMessage= true;
        updateActionEnablement();
        updateDescription();
    }
    
    public void setInput(final ITranslationUnit input) {
    	if (input == null) {
    		setMessage(IBMessages.IBViewPart_instructionMessage);
    		fTreeViewer.setInput(null);
    		return;
    	}
    	
    	if (CCorePlugin.getIndexManager().isIndexerIdle()) {
    		setInputIndexerIdle(input);
    	}
    	else {
    		setMessage(IBMessages.IBViewPart_waitingOnIndexerMessage);
    		Display disp= Display.getCurrent();
    		if (disp != null && !disp.isDisposed()) {
    			disp.timerExec(500, new Runnable() {
					public void run() {
						setInput(input);
					}});
    		}
    	}
    }

	private void setInputIndexerIdle(ITranslationUnit input) {
		fShowsMessage= false;
        boolean isHeader= input.isHeaderUnit();
        
        fTreeViewer.setInput(null);
        if (!isHeader) {
        	fContentProvider.setComputeIncludedBy(isHeader);
        	fIncludedByAction.setChecked(isHeader);
        	fIncludesToAction.setChecked(!isHeader);
        	updateSorter();
        }
        fTreeViewer.setInput(input);
        fPagebook.showPage(fViewerPage);
        updateHistory(input);

        updateActionEnablement();
        updateDescription();
	}

	private void updateActionEnablement() {
		fHistoryAction.setEnabled(!fHistoryEntries.isEmpty());
		fNextAction.setEnabled(!fShowsMessage);
		fPreviousAction.setEnabled(!fShowsMessage);
		fRefreshAction.setEnabled(!fShowsMessage);
	}

	public void createPartControl(Composite parent) {
        fPagebook = new PageBook(parent, SWT.NULL);
        fPagebook.setLayoutData(new GridData(GridData.FILL_BOTH));
        createInfoPage();
        createViewerPage();
                
        initDragAndDrop();
        createActions();
        createContextMenu();

        getSite().setSelectionProvider(fTreeViewer);
        setMessage(IBMessages.IBViewPart_instructionMessage);
        
        initializeActionStates();
        restoreInput();
        fMemento= null;
    }
    
    private void initializeActionStates() {
        boolean includedBy= true;
        boolean filterSystem= false;
//        boolean filterInactive= false;
        
        if (fMemento != null) {
            filterSystem= TRUE.equals(fMemento.getString(KEY_FILTER_SYSTEM));
//            filterInactive= TRUE.equals(fMemento.getString(KEY_FILTER_INACTIVE));
        }
        
        fIncludedByAction.setChecked(includedBy);
        fIncludesToAction.setChecked(!includedBy);
        fContentProvider.setComputeIncludedBy(includedBy);
        
//        fFilterInactiveAction.setChecked(filterInactive);
//        fFilterInactiveAction.run();
        fFilterSystemAction.setChecked(filterSystem);
        fFilterSystemAction.run();
        updateSorter();
    }
    
    private void restoreInput() {
        if (fMemento != null) {
            String pathStr= fMemento.getString(KEY_INPUT_PATH);
            if (pathStr != null) {
                IPath path= Path.fromPortableString(pathStr);
                if (path.segmentCount() > 1) {
                    String name= path.segment(0);
                    ICProject project= CoreModel.getDefault().getCModel().getCProject(name);
                    if (project != null) {
                        ICElement celement;
                        try {
                            celement = project.findElement(path);
                            if (celement instanceof ITranslationUnit) {
                                setInput((ITranslationUnit) celement);
                            }
                        } catch (CModelException e) {
                            // ignore
                        }
                    }
                }
            }
        }
    }


    public void init(IViewSite site, IMemento memento) throws PartInitException {
        fMemento= memento;
        super.init(site, memento);
    }


    public void saveState(IMemento memento) {
        if (fWorkingSetFilter != null) {
            fWorkingSetFilter.getUI().saveState(memento, KEY_WORKING_SET_FILTER);
        }
//        memento.putString(KEY_FILTER_INACTIVE, String.valueOf(fFilterInactiveAction.isChecked()));
        memento.putString(KEY_FILTER_SYSTEM, String.valueOf(fFilterSystemAction.isChecked()));
        ITranslationUnit input= getInput();
        if (input != null) {
            IPath path= input.getPath();
            if (path != null) {
                memento.putString(KEY_INPUT_PATH, path.toPortableString());
            }
        }
        super.saveState(memento);
    }

    private void createContextMenu() {
        MenuManager manager = new MenuManager();
        manager.setRemoveAllWhenShown(true);
        manager.addMenuListener(new IMenuListener() {
            public void menuAboutToShow(IMenuManager m) {
                onContextMenuAboutToShow(m);
            }
        });
        Menu menu = manager.createContextMenu(fTreeViewer.getControl());
        fTreeViewer.getControl().setMenu(menu);
        IWorkbenchPartSite site = getSite();
        site.registerContextMenu(CUIPlugin.ID_INCLUDE_BROWSER, manager, fTreeViewer); 
    }

    private void createViewerPage() {
        Display display= getSite().getShell().getDisplay();
        fViewerPage = new Composite(fPagebook, SWT.NULL);
        fViewerPage.setLayoutData(new GridData(GridData.FILL_BOTH));
        fViewerPage.setSize(100, 100);
        fViewerPage.setLayout(new FillLayout());

        fContentProvider= new IBContentProvider(display); 
        fLabelProvider= new IBLabelProvider(display, fContentProvider);
        fTreeViewer= new ExtendedTreeViewer(fViewerPage);
        fTreeViewer.setUseHashlookup(true);
        fTreeViewer.setContentProvider(fContentProvider);
        fTreeViewer.setLabelProvider(fLabelProvider);
        fTreeViewer.setAutoExpandLevel(2);     
        fTreeViewer.addOpenListener(new IOpenListener() {
            public void open(OpenEvent event) {
                onShowInclude(event.getSelection());
            }
        });
    }
    
    private void createInfoPage() {
        fInfoPage = new Composite(fPagebook, SWT.NULL);
        fInfoPage.setLayoutData(new GridData(GridData.FILL_BOTH));
        fInfoPage.setSize(100, 100);
        fInfoPage.setLayout(new FillLayout());

        fInfoText= new Text(fInfoPage, SWT.WRAP | SWT.READ_ONLY); 
    }

    private void initDragAndDrop() {
        IBDropTargetListener dropListener= new IBDropTargetListener(this);
        Transfer[] dropTransfers= new Transfer[] {
                LocalSelectionTransfer.getTransfer(),
                ResourceTransfer.getInstance(), 
                FileTransfer.getInstance()};
        DropTarget dropTarget = new DropTarget(fPagebook, DND.DROP_COPY);
        dropTarget.setTransfer(dropTransfers);
        dropTarget.addDropListener(dropListener);

        Transfer[] dragTransfers= new Transfer[] {
                ResourceTransfer.getInstance(), 
                FileTransfer.getInstance()};
        IBDragSourceListener dragListener= new IBDragSourceListener(fTreeViewer);
        dragListener.setDependentDropTargetListener(dropListener);
        fTreeViewer.addDragSupport(DND.DROP_COPY, dragTransfers, dragListener);
    }

    private void createActions() {
        WorkingSetFilterUI wsFilterUI= new WorkingSetFilterUI(this, fMemento, KEY_WORKING_SET_FILTER) {
            protected void onWorkingSetChange() {
                updateWorkingSetFilter(this);
            }
            protected void onWorkingSetNameChange() {
                updateDescription();
            }
        };

        fIncludedByAction= 
            new Action(IBMessages.IBViewPart_showIncludedBy_label, IAction.AS_RADIO_BUTTON) { 
                public void run() {
                    if (isChecked()) {
                        onSetDirection(true);
                    }
                }
        };
        fIncludedByAction.setToolTipText(IBMessages.IBViewPart_showIncludedBy_tooltip);
        CPluginImages.setImageDescriptors(fIncludedByAction, CPluginImages.T_LCL, CPluginImages.IMG_ACTION_SHOW_REF_BY);       

        fIncludesToAction= 
            new Action(IBMessages.IBViewPart_showIncludesTo_label, IAction.AS_RADIO_BUTTON) { 
                public void run() {
                    if (isChecked()) {
                        onSetDirection(false);
                    }
                }
        };
        fIncludesToAction.setToolTipText(IBMessages.IBViewPart_showIncludesTo_tooltip);
        CPluginImages.setImageDescriptors(fIncludesToAction, CPluginImages.T_LCL, CPluginImages.IMG_ACTION_SHOW_RELATES_TO);       

//        fInactiveFilter= new ViewerFilter() {
//            public boolean select(Viewer viewer, Object parentElement, Object element) {
//                if (element instanceof IBNode) {
//                    IBNode node= (IBNode) element;
//                    return node.isActiveCode();
//                }
//                return true;
//            }
//        };
//        fFilterInactiveAction= new Action(IBMessages.IBViewPart_hideInactive_label, IAction.AS_CHECK_BOX) {
//            public void run() {
//                if (isChecked()) {
//                    fTreeViewer.addFilter(fInactiveFilter);
//                }
//                else {
//                    fTreeViewer.removeFilter(fInactiveFilter);
//                }
//            }
//        };
//        fFilterInactiveAction.setToolTipText(IBMessages.IBViewPart_hideInactive_tooltip);
//        CPluginImages.setImageDescriptors(fFilterInactiveAction, CPluginImages.T_LCL, CPluginImages.IMG_ACTION_HIDE_INACTIVE);       

        fSystemFilter= new ViewerFilter() {
            public boolean select(Viewer viewer, Object parentElement, Object element) {
                if (element instanceof IBNode) {
                    IBNode node= (IBNode) element;
                    return !node.isSystemInclude();
                }
                return true;
            }
        };
        fFilterSystemAction= new Action(IBMessages.IBViewPart_hideSystem_label, IAction.AS_CHECK_BOX) {
            public void run() {
                if (isChecked()) {
                    fTreeViewer.addFilter(fSystemFilter);
                }
                else {
                    fTreeViewer.removeFilter(fSystemFilter);
                }
            }
        };
        fFilterSystemAction.setToolTipText(IBMessages.IBViewPart_hideSystem_tooltip);
        CPluginImages.setImageDescriptors(fFilterSystemAction, CPluginImages.T_LCL, CPluginImages.IMG_ACTION_HIDE_SYSTEM);       
        
        fSorterAlphaNumeric= new ViewerComparator();
        fSorterReferencePosition= new ViewerComparator() {
            public int category(Object element) {
                if (element instanceof IBNode) {
                    return 0;
                }
                return 1;
            }
            public int compare(Viewer viewer, Object e1, Object e2) {
                if (!(e1 instanceof IBNode)) {
                    if (!(e2 instanceof IBNode)) {
                        return 0;
                    }
                    return -1;
                }
                if (!(e2 instanceof IBNode)) {
                    return 1;
                }
                IBNode n1= (IBNode) e1;
                IBNode n2= (IBNode) e2;
                return n1.getDirectiveCharacterOffset() - n2.getDirectiveCharacterOffset();
            }
        };
        
        fShowFolderInLabelsAction= new Action(IBMessages.IBViewPart_showFolders_label, IAction.AS_CHECK_BOX) {
            public void run() {
                onShowFolderInLabels(isChecked());
            }
        };
        fShowFolderInLabelsAction.setToolTipText(IBMessages.IBViewPart_showFolders_tooltip);
        fNextAction = new Action(IBMessages.IBViewPart_nextMatch_label) {
            public void run() {
                onNextOrPrevious(true);
            }
        };
        fNextAction.setToolTipText(IBMessages.IBViewPart_nextMatch_tooltip); 
        CPluginImages.setImageDescriptors(fNextAction, CPluginImages.T_LCL, CPluginImages.IMG_SHOW_NEXT);       

        fPreviousAction = new Action(IBMessages.IBViewPart_previousMatch_label) {
            public void run() {
                onNextOrPrevious(false);
            }
        };
        fPreviousAction.setToolTipText(IBMessages.IBViewPart_previousMatch_tooltip); 
        CPluginImages.setImageDescriptors(fPreviousAction, CPluginImages.T_LCL, CPluginImages.IMG_SHOW_PREV);       

        fRefreshAction = new Action(IBMessages.IBViewPart_refresh_label) {
            public void run() {
                onRefresh();
            }
        };
        fRefreshAction.setToolTipText(IBMessages.IBViewPart_refresh_tooltip); 
        CPluginImages.setImageDescriptors(fRefreshAction, CPluginImages.T_LCL, CPluginImages.IMG_REFRESH);       

        fHistoryAction= new IBHistoryDropDownAction(this);
        
        // setup action bar
        // global action hooks
        IActionBars actionBars = getViewSite().getActionBars();
        actionBars.setGlobalActionHandler(ActionFactory.NEXT.getId(), fNextAction);
        actionBars.setGlobalActionHandler(ActionFactory.PREVIOUS.getId(), fPreviousAction);
        actionBars.setGlobalActionHandler(ActionFactory.REFRESH.getId(), fRefreshAction);
        actionBars.updateActionBars();
        
        // local toolbar
        IToolBarManager tm = actionBars.getToolBarManager();
        tm.add(fNextAction);
        tm.add(fPreviousAction);
        tm.add(new Separator());
        tm.add(fFilterSystemAction);
//        tm.add(fFilterInactiveAction);
        tm.add(new Separator());
        tm.add(fIncludedByAction);
        tm.add(fIncludesToAction);
        tm.add(fHistoryAction);
        tm.add(fRefreshAction);

        // local menu
        IMenuManager mm = actionBars.getMenuManager();

//        tm.add(fNext);
//        tm.add(fPrevious);
//        tm.add(new Separator());
        wsFilterUI.fillActionBars(actionBars);
        mm.add(fIncludedByAction);
        mm.add(fIncludesToAction);
        mm.add(new Separator());
        mm.add(fShowFolderInLabelsAction);
        mm.add(new Separator());
        mm.add(fFilterSystemAction);
//        mm.add(fFilterInactiveAction);
    }
    
    private IBNode getNextNode(boolean forward) {
    	TreeNavigator navigator= new TreeNavigator(fTreeViewer.getTree(), IBNode.class);
    	TreeItem selectedItem= navigator.getSelectedItemOrFirstOnLevel(1, forward);
    	if (selectedItem == null) {
    		return null;
    	}
    	
        if (selectedItem.getData().equals(fLastNavigationNode)) {
        	selectedItem= navigator.getNextSibbling(selectedItem, forward);
        }
        
        return selectedItem == null ? null : (IBNode) selectedItem.getData();
    }
        
    protected void onNextOrPrevious(boolean forward) {
    	IBNode nextItem= getNextNode(forward);
        if (nextItem != null) {
            StructuredSelection sel= new StructuredSelection(nextItem);
            fTreeViewer.setSelection(sel);
            onShowInclude(sel);
        }
    }

    protected void onRefresh() {
        fContentProvider.recompute();
    }
    
    protected void onShowFolderInLabels(boolean show) {
        fLabelProvider.setShowFolders(show);
        fTreeViewer.refresh();
    }

    private void updateHistory(ITranslationUnit input) {
    	if (input != null) {
    		fHistoryEntries.remove(input);
    		fHistoryEntries.add(0, input);
    		if (fHistoryEntries.size() > MAX_HISTORY_SIZE) {
    			fHistoryEntries.remove(MAX_HISTORY_SIZE-1);
    		}
    	}
	}

    private void updateSorter() {
        if (fIncludedByAction.isChecked()) {
            fTreeViewer.setComparator(fSorterAlphaNumeric);
        }
        else {
            fTreeViewer.setComparator(fSorterReferencePosition);
        }
    }
    
    private void updateDescription() {
        String message= ""; //$NON-NLS-1$
        if (!fShowsMessage) {
        	ITranslationUnit tu= getInput();
            if (tu != null) {
                IPath path= tu.getPath();
                if (path != null) {
                    String format, file, scope;
                    
                    file= path.lastSegment() + "(" + path.removeLastSegments(1) + ")";  //$NON-NLS-1$//$NON-NLS-2$
                    if (fWorkingSetFilter == null) {
                        scope= IBMessages.IBViewPart_workspaceScope;
                    }
                    else {
                        scope= fWorkingSetFilter.getLabel();
                    }
                    
                    if (fIncludedByAction.isChecked()) {
                        format= IBMessages.IBViewPart_IncludedByContentDescription;
                    }
                    else {
                        format= IBMessages.IBViewPart_IncludesToContentDescription;
                    }
                    message= Messages.format(format, file, scope);
                }
            }
        }
        setContentDescription(message);
    }

    private void updateWorkingSetFilter(WorkingSetFilterUI filterUI) {
        if (filterUI.getWorkingSet() == null) {
            if (fWorkingSetFilter != null) {
                fTreeViewer.removeFilter(fWorkingSetFilter);
                fWorkingSetFilter= null;
            }
        }
        else {
            if (fWorkingSetFilter != null) {
                fTreeViewer.refresh();
            }
            else {
                fWorkingSetFilter= new IBWorkingSetFilter(filterUI);
                fTreeViewer.addFilter(fWorkingSetFilter);
            }
        }
    }
    
    protected void onSetDirection(boolean includedBy) {
        if (includedBy != fContentProvider.getComputeIncludedBy()) {
            Object input= fTreeViewer.getInput();
            fTreeViewer.setInput(null);
            fContentProvider.setComputeIncludedBy(includedBy);
            updateSorter();
            fTreeViewer.setInput(input);
            updateDescription();
        }
    }

    protected void onContextMenuAboutToShow(IMenuManager m) {
        final IStructuredSelection selection = (IStructuredSelection) fTreeViewer.getSelection();
        final IBNode node= IBConversions.selectionToNode(selection);
        
        if (node != null) {
            final IWorkbenchPage page = getSite().getPage();
            
            // open include
            if (node.getParent() != null && node.getDirectiveFile() != null) {
                m.add(new Action(IBMessages.IBViewPart_showInclude_label) {
                    public void run() {
                        onShowInclude(selection);
                    }
                });
            }

            final ITranslationUnit tu= node.getRepresentedTranslationUnit();
            if (tu != null) {
                // open
                OpenFileAction ofa= new OpenFileAction(page);
                ofa.selectionChanged(selection);
                m.add(ofa);

                // open with
                // keep the menu shorter, no open with support
//                final IResource r= tu.getResource();
//                if (r != null) {
//                    IMenuManager submenu= new MenuManager(IBMessages.IBViewPart_OpenWithMenu_label); 
//                    submenu.add(new OpenWithMenu(page, r));
//                    m.add(submenu);
//                }

                // show in
                IMenuManager submenu= new MenuManager(IBMessages.IBViewPart_ShowInMenu_label);
                submenu.add(ContributionItemFactory.VIEWS_SHOW_IN.create(getSite().getWorkbenchWindow()));
                m.add(submenu);
            	if (node.getParent() != null) {
                    m.add(new Separator());
            		m.add(new Action(Messages.format(IBMessages.IBViewPart_FocusOn_label, tu.getPath().lastSegment())) {
            			public void run() {
            				setInput(tu);
            			}
            		});
            	}

            }
        }
        m.add(new Separator(IContextMenuConstants.GROUP_ADDITIONS));
    }
    
    protected void onShowInclude(ISelection selection) {
        IBNode node= IBConversions.selectionToNode(selection);
        if (node != null) {
        	fLastNavigationNode= node;

            IWorkbenchPage page= getSite().getPage();
        	IBFile ibf= node.getDirectiveFile();
            if (ibf != null) {
                IRegion region= new Region(node.getDirectiveCharacterOffset(), node.getDirectiveLength());
                long timestamp= node.getTimestamp();

                IFile f= ibf.getResource();
                if (f != null) {
                	EditorOpener.open(page, f, region, timestamp);
                }
                else {
                    IPath location= ibf.getLocation();
                    if (location != null) {
                    	EditorOpener.openExternalFile(page, location, region, timestamp);
                    }
                }
            }
            else {
            	ITranslationUnit tu= IBConversions.selectionToTU(selection);
            	if (tu != null) {
            		IResource r= tu.getResource();
            		if (r != null) {
            			OpenFileAction ofa= new OpenFileAction(page);
            			ofa.selectionChanged((IStructuredSelection) selection);
            			ofa.run();
            		}
            	}
            }
        }
    }

    public ShowInContext getShowInContext() {
        return new ShowInContext(null, fTreeViewer.getSelection());
    }

    public boolean show(ShowInContext context) {
        ITranslationUnit tu= IBConversions.selectionToTU(context.getSelection());
        if (tu == null) {
            tu= IBConversions.objectToTU(context.getInput());
            if (tu == null) {
                setMessage(IBMessages.IBViewPart_falseInputMessage);
                return false;
            }
        }

        setInput(tu);
        return true;
    }
    
    public String[] getShowInTargetIds() {
        return new String[] {
        		CUIPlugin.CVIEW_ID, 
        		IPageLayout.ID_RES_NAV
        };
    }

	public Control getPageBook() {
		return fPagebook;
	}

	public ITranslationUnit[] getHistoryEntries() {
		return (ITranslationUnit[]) fHistoryEntries.toArray(new ITranslationUnit[fHistoryEntries.size()]);
	}

	public void setHistoryEntries(ITranslationUnit[] remaining) {
		fHistoryEntries.clear();
		fHistoryEntries.addAll(Arrays.asList(remaining));
	}

	public ITranslationUnit getInput() {
        Object input= fTreeViewer.getInput();
        if (input instanceof ITranslationUnit) {
        	return (ITranslationUnit) input;
        }
        return null;
	}
}
