/*   **********************************************************************  **
 **   Copyright notice                                                       **
 **                                                                          **
 **   (c) 2005-2009 RSSOwl Development Team                                  **
 **   http://www.rssowl.org/                                                 **
 **                                                                          **
 **   All rights reserved                                                    **
 **                                                                          **
 **   This program and the accompanying materials are made available under   **
 **   the terms of the Eclipse Public License v1.0 which accompanies this    **
 **   distribution, and is available at:                                     **
 **   http://www.rssowl.org/legal/epl-v10.html                               **
 **                                                                          **
 **   A copy is found in the file epl-v10.html and important notices to the  **
 **   license from the team is found in the textfile LICENSE.txt distributed **
 **   in this package.                                                       **
 **                                                                          **
 **   This copyright notice MUST APPEAR in all copies of the file!           **
 **                                                                          **
 **   Contributors:                                                          **
 **     RSSOwl Development Team - initial API and implementation             **
 **                                                                          **
 **  **********************************************************************  */

package org.rssowl.core.internal.persist.dao;

import org.rssowl.core.internal.persist.SearchFilter;
import org.rssowl.core.persist.ISearchFilter;
import org.rssowl.core.persist.dao.ISearchFilterDAO;
import org.rssowl.core.persist.event.SearchFilterEvent;
import org.rssowl.core.persist.event.SearchFilterListener;

/**
 * @author bpasero
 */
public class SearchFilterDAOImpl extends AbstractEntityDAO<ISearchFilter, SearchFilterListener, SearchFilterEvent> implements ISearchFilterDAO {

  /** Default constructor using the specific IPersistable for this DAO */
  public SearchFilterDAOImpl() {
    super(SearchFilter.class, true);
  }

  /*
   * @see
   * org.rssowl.core.internal.persist.dao.AbstractEntityDAO#createSaveEventTemplate
   * (org.rssowl.core.persist.IEntity)
   */
  @Override
  protected SearchFilterEvent createSaveEventTemplate(ISearchFilter entity) {
    return new SearchFilterEvent(entity, true);
  }

  /*
   * @seeorg.rssowl.core.internal.persist.dao.AbstractEntityDAO#
   * createDeleteEventTemplate(org.rssowl.core.persist.IEntity)
   */
  @Override
  protected SearchFilterEvent createDeleteEventTemplate(ISearchFilter entity) {
    return createSaveEventTemplate(entity);
  }
}