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
package org.rssowl.core.internal.persist;

import org.rssowl.core.persist.IFolder;
import org.rssowl.core.persist.INews;
import org.rssowl.core.persist.INewsBin;
import org.rssowl.core.persist.INews.State;
import org.rssowl.core.persist.reference.NewsBinReference;
import org.rssowl.core.persist.reference.NewsReference;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class NewsBin extends Mark implements INewsBin   {
  private NewsContainer fNewsContainer;

  /**
   * Creates a new Element of the type SearchMark. A SearchMark is only visually
   * represented in case it was added to a Folder. Make sure to add it to a
   * Folder using <code>Folder#addMark(Mark)</code>
   *
   * @param id The unique ID of this SearchMark.
   * @param folder The Folder this SearchMark belongs to.
   * @param name The Name of this SearchMark.
   */
  public NewsBin(Long id, IFolder folder, String name) {
    super(id, folder, name);
    fNewsContainer = new NewsContainer(Collections.<INews.State, Boolean>emptyMap());
  }

  /**
   * Default constructor for deserialization
   */
  protected NewsBin() {
    super();
  }

  /**
   * Should be used for testing only.
   * @return internal NewsContainer.
   */
  public synchronized NewsContainer internalGetNewsContainer() {
    return fNewsContainer;
  }

  public synchronized void addNews(INews news) {
    fNewsContainer.addNews(news);
  }

  public synchronized boolean containsNews(INews news) {
    return fNewsContainer.containsNews(news);
  }

  public synchronized List<NewsReference> getNewsRefs() {
    return fNewsContainer.getNews();
  }

  public synchronized int getNewsCount(Set<State> states) {
    return fNewsContainer.getNewsCount(states);
  }

  public synchronized void removeNews(INews news) {
    fNewsContainer.removeNews(news);
  }

  /**
   * Removes each NewsReference contained in this NewsBin that matches any of
   * the ones in {@code newsRefs}. This method is not exposed in the INewsBin
   * interface because it should only be used in rare cases.
   * @param newsRefs
   */
  public synchronized void removeNewsRefs(List<NewsReference> newsRefs) {
    fNewsContainer.removeNewsRefs(newsRefs);
  }

  public synchronized List<INews> getNews() {
    return getNews(EnumSet.allOf(INews.State.class));
  }

  public List<INews> getNews(Set<State> states) {
    List<NewsReference> newsRefs;
    synchronized (this) {
      newsRefs = fNewsContainer.getNews(states);
    }
    return getNews(newsRefs);
  }

  public synchronized List<NewsReference> getNewsRefs(Set<State> states) {
    return fNewsContainer.getNews(states);
  }

  public NewsBinReference toReference() {
    return new NewsBinReference(getIdAsPrimitive());
  }

  public boolean isGetNewsRefsEfficient() {
    return true;
  }

  public synchronized boolean updateNewsStates(Collection<StatesUpdateInfo> statesUpdateInfos) {
    return fNewsContainer.updateNewsStates(statesUpdateInfos);
  }

  public synchronized Collection<NewsReference> removeNews(Set<State> states) {
    return fNewsContainer.removeNews(states);
  }
}