package org.apache.hadoop.hive.llap.daemon.impl;

import java.util.*;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/*
 * Copyright (c) 2007, Aviad Ben Dov
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list
 * of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this
 * list of conditions and the following disclaimer in the documentation and/or other
 * materials provided with the distribution.
 * 3. Neither the name of Infomancers, Ltd. nor the names of its contributors may be
 * used to endorse or promote products derived from this software without specific
 * prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */


/**
 * An optionally-bounded {@linkplain BlockingDeque blocking deque} based on
 * a navigable set.
 * <br>
 * <p> The optional capacity bound constructor argument serves as a
 * way to prevent excessive expansion. The capacity, if unspecified,
 * is equal to {@link Integer#MAX_VALUE}.
 * <br>
 * <p>This class and its iterator implement all of the
 * <em>optional</em> methods of the {@link Collection} and {@link
 * Iterator} interfaces.
 * <br>
 * This code is loosely based on the {@linkplain java.util.concurrent.LinkedBlockingDeque linked blocking deque} code.
 *
 * @author Aviad Ben Dov
 * @param <E> the type of elements held in this collection
 * @since 0.3
 */
public class PriorityBlockingDeque<E>
    extends AbstractQueue<E>
    implements BlockingDeque<E>, java.io.Serializable {

    /*
     * Implemented as a navigable set protected by a
     * single lock and using conditions to manage blocking.
     */

  private final int capacity;

  private final LinkedList<E> list;
  /**
   * Main lock guarding all access
   */
  private final ReentrantLock lock = new ReentrantLock();
  /**
   * Condition for waiting takes
   */
  private final Condition notEmpty = lock.newCondition();
  /**
   * Condition for waiting puts
   */
  private final Condition notFull = lock.newCondition();
  private Comparator<E> comparator;

  /**
   * Creates a <pre>PriorityBlockingDeque</pre> with a capacity of
   * {@link Integer#MAX_VALUE}.
   */
  public PriorityBlockingDeque() {
    this(null, Integer.MAX_VALUE);
  }

  /**
   * Creates a <pre>PriorityBlockingDeque</pre> with the given (fixed) capacity.
   *
   * @param capacity the capacity of this deque
   * @throws IllegalArgumentException if <pre>capacity</pre> is less than 1
   */
  public PriorityBlockingDeque(int capacity) {
    this(null, capacity);
  }

  public PriorityBlockingDeque(Comparator<E> comparator) {
    this(comparator, Integer.MAX_VALUE);
  }

  public PriorityBlockingDeque(Comparator<E> comparator, int capacity) {
    if (capacity <= 0) throw new IllegalArgumentException();
    this.capacity = capacity;
    this.list = new LinkedList<E>();
    this.comparator = comparator;
  }

  // Basic adding and removing operations, called only while holding lock

  /**
   * Adds e or returns false if full.
   *
   * @param e The element to add.
   * @return Whether adding was successful.
   */
  private boolean innerAdd(E e) {
    if (list.size() >= capacity)
      return false;

    int insertionPoint = Collections.binarySearch(list, e, comparator);
    if (insertionPoint < 0) {
      // this means the key didn't exist, so the insertion point is negative minus 1.
      insertionPoint = -insertionPoint - 1;
    }

    list.add(insertionPoint, e);
    // Inserted in sort order. Hence no explict sort.
    notEmpty.signal();

    return true;
  }

  /**
   * Removes and returns first element, or null if empty.
   *
   * @return The removed element.
   */
  private E innerRemoveFirst() {
    E f = list.pollFirst();
    if (f == null)
      return null;


    notFull.signal();
    return f;
  }

  /**
   * Removes and returns last element, or null if empty.
   *
   * @return The removed element.
   */
  private E innerRemoveLast() {
    E l = list.pollLast();
    if (l == null)
      return null;

    notFull.signal();
    return l;
  }

  // BlockingDeque methods

  /**
   * @throws IllegalStateException {@inheritDoc}
   * @throws NullPointerException  {@inheritDoc}
   */
  public void addFirst(E e) {
    if (!offerFirst(e))
      throw new IllegalStateException("Deque full");
  }

  /**
   * @throws IllegalStateException {@inheritDoc}
   * @throws NullPointerException  {@inheritDoc}
   */
  public void addLast(E e) {
    if (!offerLast(e))
      throw new IllegalStateException("Deque full");
  }

  /**
   * @throws NullPointerException {@inheritDoc}
   */
  public boolean offerFirst(E e) {
    if (e == null) throw new NullPointerException();
    lock.lock();
    try {
      return innerAdd(e);
    } finally {
      lock.unlock();
    }
  }

  /**
   * @throws NullPointerException {@inheritDoc}
   */
  @Override
  public boolean offerLast(E e) {
    if (e == null) throw new NullPointerException();
    lock.lock();
    try {
      return innerAdd(e);
    } finally {
      lock.unlock();
    }
  }

  /**
   * @throws NullPointerException {@inheritDoc}
   * @throws InterruptedException {@inheritDoc}
   */
  public void putFirst(E e) throws InterruptedException {
    if (e == null) throw new NullPointerException();
    lock.lock();
    try {
      while (!innerAdd(e))
        notFull.await();
    } finally {
      lock.unlock();
    }
  }

  /**
   * @throws NullPointerException {@inheritDoc}
   * @throws InterruptedException {@inheritDoc}
   */
  public void putLast(E e) throws InterruptedException {
    if (e == null) throw new NullPointerException();
    lock.lock();
    try {
      while (!innerAdd(e))
        notFull.await();
    } finally {
      lock.unlock();
    }
  }

  /**
   * @throws NullPointerException {@inheritDoc}
   * @throws InterruptedException {@inheritDoc}
   */
  public boolean offerFirst(E e, long timeout, TimeUnit unit)
      throws InterruptedException {
    if (e == null) throw new NullPointerException();
    long nanos = unit.toNanos(timeout);
    lock.lockInterruptibly();
    try {
      for (; ;) {
        if (innerAdd(e))
          return true;
        if (nanos <= 0)
          return false;
        nanos = notFull.awaitNanos(nanos);
      }
    } finally {
      lock.unlock();
    }
  }

  /**
   * @throws NullPointerException {@inheritDoc}
   * @throws InterruptedException {@inheritDoc}
   */
  public boolean offerLast(E e, long timeout, TimeUnit unit)
      throws InterruptedException {
    if (e == null) throw new NullPointerException();
    long nanos = unit.toNanos(timeout);
    lock.lockInterruptibly();
    try {
      for (; ;) {
        if (innerAdd(e))
          return true;
        if (nanos <= 0)
          return false;
        nanos = notFull.awaitNanos(nanos);
      }
    } finally {
      lock.unlock();
    }
  }

  /**
   * @throws NoSuchElementException {@inheritDoc}
   */
  public E removeFirst() {
    E x = pollFirst();
    if (x == null) throw new NoSuchElementException();
    return x;
  }

  /**
   * @throws NoSuchElementException {@inheritDoc}
   */
  public E removeLast() {
    E x = pollLast();
    if (x == null) throw new NoSuchElementException();
    return x;
  }

  public E pollFirst() {
    lock.lock();
    try {
      return innerRemoveFirst();
    } finally {
      lock.unlock();
    }
  }

  public E pollLast() {
    lock.lock();
    try {
      return innerRemoveLast();
    } finally {
      lock.unlock();
    }
  }

  public E takeFirst() throws InterruptedException {
    lock.lock();
    try {
      E x;
      while ((x = innerRemoveFirst()) == null)
        notEmpty.await();
      return x;
    } finally {
      lock.unlock();
    }
  }

  public E takeLast() throws InterruptedException {
    lock.lock();
    try {
      E x;
      while ((x = innerRemoveLast()) == null)
        notEmpty.await();
      return x;
    } finally {
      lock.unlock();
    }
  }

  public E pollFirst(long timeout, TimeUnit unit)
      throws InterruptedException {
    long nanos = unit.toNanos(timeout);
    lock.lockInterruptibly();
    try {
      for (; ;) {
        E x = innerRemoveFirst();
        if (x != null)
          return x;
        if (nanos <= 0)
          return null;
        nanos = notEmpty.awaitNanos(nanos);
      }
    } finally {
      lock.unlock();
    }
  }

  public E pollLast(long timeout, TimeUnit unit)
      throws InterruptedException {
    long nanos = unit.toNanos(timeout);
    lock.lockInterruptibly();
    try {
      for (; ;) {
        E x = innerRemoveLast();
        if (x != null)
          return x;
        if (nanos <= 0)
          return null;
        nanos = notEmpty.awaitNanos(nanos);
      }
    } finally {
      lock.unlock();
    }
  }

  /**
   * @throws NoSuchElementException {@inheritDoc}
   */
  public E getFirst() {
    E x = peekFirst();
    if (x == null) throw new NoSuchElementException();
    return x;
  }

  /**
   * @throws NoSuchElementException {@inheritDoc}
   */
  public E getLast() {
    E x = peekLast();
    if (x == null) throw new NoSuchElementException();
    return x;
  }

  public E peekFirst() {
    lock.lock();
    try {
      return list.size() == 0 ? null : list.peekFirst();
    } finally {
      lock.unlock();
    }
  }

  public E peekLast() {
    lock.lock();
    try {
      return list.size() == 0 ? null : list.peekLast();
    } finally {
      lock.unlock();
    }
  }

  public boolean removeFirstOccurrence(Object o) {
    if (o == null) return false;
    lock.lock();
    try {
      for (Iterator<E> it = list.iterator(); it.hasNext();) {
        E e = it.next();
        if (o.equals(e)) {
          it.remove();
          return true;
        }
      }
      return false;
    } finally {
      lock.unlock();
    }
  }

  public boolean removeLastOccurrence(Object o) {
    if (o == null) return false;
    lock.lock();
    try {
      for (Iterator<E> it = list.descendingIterator(); it.hasNext();) {
        E e = it.next();
        if (o.equals(e)) {
          it.remove();
          return true;
        }
      }
      return false;
    } finally {
      lock.unlock();
    }
  }

  // BlockingQueue methods

  /**
   * Inserts the specified element to the deque unless it would
   * violate capacity restrictions.  When using a capacity-restricted deque,
   * it is generally preferable to use method {@link #offer(Object) offer}.
   * <br>
   * This method is equivalent to {@link #addLast}.
   *
   * @throws IllegalStateException if the element cannot be added at this
   *                               time due to capacity restrictions
   * @throws NullPointerException  if the specified element is null
   */
  @Override
  public boolean add(E e) {
    addLast(e);
    return true;
  }

  /**
   * @throws NullPointerException if the specified element is null
   */
  @Override
  public boolean offer(E e) {
    return offerLast(e);
  }

  /**
   * @throws NullPointerException {@inheritDoc}
   * @throws InterruptedException {@inheritDoc}
   */
  public void put(E e) throws InterruptedException {
    putLast(e);
  }

  /**
   * @throws NullPointerException {@inheritDoc}
   * @throws InterruptedException {@inheritDoc}
   */
  public boolean offer(E e, long timeout, TimeUnit unit)
      throws InterruptedException {
    return offerLast(e, timeout, unit);
  }

  /**
   * Retrieves and removes the head of the queue represented by this deque.
   * This method differs from {@link #poll poll} only in that it throws an
   * exception if this deque is empty.
   * <br>
   * This method is equivalent to {@link #removeFirst() removeFirst}.
   *
   * @return the head of the queue represented by this deque
   * @throws NoSuchElementException if this deque is empty
   */
  @Override
  public E remove() {
    return removeFirst();
  }

  public E poll() {
    return pollFirst();
  }

  public E take() throws InterruptedException {
    return takeFirst();
  }

  public E poll(long timeout, TimeUnit unit) throws InterruptedException {
    return pollFirst(timeout, unit);
  }

  /**
   * Retrieves, but does not remove, the head of the queue represented by
   * this deque.  This method differs from {@link #peek peek} only in that
   * it throws an exception if this deque is empty.
   * <br>
   * This method is equivalent to {@link #getFirst() getFirst}.
   *
   * @return the head of the queue represented by this deque
   * @throws NoSuchElementException if this deque is empty
   */
  @Override
  public E element() {
    return getFirst();
  }

  public E peek() {
    return peekFirst();
  }

  /**
   * Returns the number of additional elements that this deque can ideally
   * (in the absence of memory or resource constraints) accept without
   * blocking. This is always equal to the initial capacity of this deque
   * less the current <pre>size</pre> of this deque.
   * <br>
   * Note that you <em>cannot</em> always tell if an attempt to insert
   * an element will succeed by inspecting <pre>remainingCapacity</pre>
   * because it may be the case that another thread is about to
   * insert or remove an element.
   */
  public int remainingCapacity() {
    lock.lock();
    try {
      return capacity - list.size();
    } finally {
      lock.unlock();
    }
  }

  /**
   * @throws UnsupportedOperationException {@inheritDoc}
   * @throws ClassCastException            {@inheritDoc}
   * @throws NullPointerException          {@inheritDoc}
   * @throws IllegalArgumentException      {@inheritDoc}
   */
  public int drainTo(Collection<? super E> c) {
    if (c == null)
      throw new NullPointerException();
    if (c == this)
      throw new IllegalArgumentException();
    lock.lock();
    try {
      for (E e : list) {
        c.add(e);
      }
      int n = list.size();
      list.clear();
      notFull.signalAll();
      return n;
    } finally {
      lock.unlock();
    }
  }

  /**
   * @throws UnsupportedOperationException {@inheritDoc}
   * @throws ClassCastException            {@inheritDoc}
   * @throws NullPointerException          {@inheritDoc}
   * @throws IllegalArgumentException      {@inheritDoc}
   */
  public int drainTo(Collection<? super E> c, int maxElements) {
    if (c == null)
      throw new NullPointerException();
    if (c == this)
      throw new IllegalArgumentException();
    lock.lock();
    try {
      int n = 0;
      for (Iterator<E> it = list.iterator(); n < maxElements && it.hasNext();) {
        E e = it.next();
        c.add(e);
        it.remove();
        ++n;
      }

      notFull.signalAll();
      return n;
    } finally {
      lock.unlock();
    }
  }

  // Stack methods

  /**
   * @throws IllegalStateException {@inheritDoc}
   * @throws NullPointerException  {@inheritDoc}
   */
  public void push(E e) {
    addFirst(e);
  }

  /**
   * @throws NoSuchElementException {@inheritDoc}
   */
  public E pop() {
    return removeFirst();
  }

  // Collection methods

  /**
   * Removes the first occurrence of the specified element from this deque.
   * If the deque does not contain the element, it is unchanged.
   * More formally, removes the first element <pre>e</pre> such that
   * <pre>o.equals(e)</pre> (if such an element exists).
   * Returns <pre>true</pre> if this deque contained the specified element
   * (or equivalently, if this deque changed as a result of the call).
   * <br>
   * This method is equivalent to
   * {@link #removeFirstOccurrence(Object) removeFirstOccurrence}.
   *
   * @param o element to be removed from this deque, if present
   * @return <pre>true</pre> if this deque changed as a result of the call
   */
  @Override
  public boolean remove(Object o) {
    return removeFirstOccurrence(o);
  }

  /**
   * Returns the number of elements in this deque.
   *
   * @return the number of elements in this deque
   */
  @Override
  public int size() {
    lock.lock();
    try {
      return list.size();
    } finally {
      lock.unlock();
    }
  }

  /**
   * Returns <pre>true</pre> if this deque contains the specified element.
   * More formally, returns <pre>true</pre> if and only if this deque contains
   * at least one element <pre>e</pre> such that <pre>o.equals(e)</pre>.
   *
   * @param o object to be checked for containment in this deque
   * @return <pre>true</pre> if this deque contains the specified element
   */
  @Override
  public boolean contains(Object o) {
    if (o == null) return false;
    lock.lock();
    try {
      return list.contains(o);
    } finally {
      lock.unlock();
    }
  }

  /**
   * Returns an array containing all of the elements in this deque, in
   * proper sequence (from first to last element).
   * <br>
   * <p>The returned array will be "safe" in that no references to it are
   * maintained by this deque.  (In other words, this method must allocate
   * a new array).  The caller is thus free to modify the returned array.
   * </p>
   * <br>This method acts as bridge between array-based and collection-based
   * APIs.
   *
   * @return an array containing all of the elements in this deque
   */
  @Override
  public Object[] toArray() {
    lock.lock();
    try {
      return list.toArray();
    } finally {
      lock.unlock();
    }
  }

  /**
   * Returns an array containing all of the elements in this deque, in
   * proper sequence; the runtime type of the returned array is that of
   * the specified array.  If the deque fits in the specified array, it
   * is returned therein.  Otherwise, a new array is allocated with the
   * runtime type of the specified array and the size of this deque.
   * <br>
   * If this deque fits in the specified array with room to spare
   * (i.e., the array has more elements than this deque), the element in
   * the array immediately following the end of the deque is set to
   * <pre>null</pre>.
   * <p>Like the {@link #toArray()} method, this method acts as bridge between
   * array-based and collection-based APIs.  Further, this method allows
   * precise control over the runtime type of the output array, and may,
   * under certain circumstances, be used to save allocation costs.
   * </p>
   * Suppose <pre>x</pre> is a deque known to contain only strings.
   * The following code can be used to dump the deque into a newly
   * allocated array of <pre>String</pre>:
   * <pre>
   *     String[] y = x.toArray(new String[0]);</pre>
   * <br>
   * Note that <pre>toArray(new Object[0])</pre> is identical in function to
   * <pre>toArray()</pre>.
   *
   * @param a the array into which the elements of the deque are to
   *          be stored, if it is big enough; otherwise, a new array of the
   *          same runtime type is allocated for this purpose
   * @return an array containing all of the elements in this deque
   * @throws ArrayStoreException  if the runtime type of the specified array
   *                              is not a supertype of the runtime type of every element in
   *                              this deque
   * @throws NullPointerException if the specified array is null
   */
  @Override
  public <T> T[] toArray(T[] a) {
    lock.lock();
    try {
      return list.toArray(a);
    } finally {
      lock.unlock();
    }
  }

  @Override
  public String toString() {
    lock.lock();
    try {
      return super.toString();
    } finally {
      lock.unlock();
    }
  }

  /**
   * Atomically removes all of the elements from this deque.
   * The deque will be empty after this call returns.
   */
  @Override
  public void clear() {
    lock.lock();
    try {
      list.clear();
      notFull.signalAll();
    } finally {
      lock.unlock();
    }
  }

  @Override
  public Iterator<E> iterator() {
    return list.iterator();
  }

  public Iterator<E> descendingIterator() {
    return list.descendingIterator();
  }
}