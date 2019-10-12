import java.util.concurrent.Semaphore;

/**
 * ReadWriteSemLock is the locking implementation for the file system
 * 
 * It allows for a maximum of 10 concurrent read requests, and only 1 write
 * request.
 * 
 * This is due to the fact that there is a max buffer of 10 for the read semaphore.
 * Also, if the read is starting from a closed state, it also obtains the write lock
 * to prevent any writes while being read.
 * 
 * Similarly, when the write lock is being obtained (it can only be obtained if all write
 * and read locks are available), then it also allocated all of the read locks
 * in order to prevent any reads while being written to.
 */
public class ReadWriteSemLock {
  private Semaphore readSem;
  private Semaphore writeSem;
  private int MAXREADS;
  private Mode mode;

  public ReadWriteSemLock() {
    this.MAXREADS = 10;
    this.readSem = new Semaphore(MAXREADS);
    this.writeSem = new Semaphore(1);
    this.mode = Mode.CLOSED;
  }

  public void acquireReadLock() {
    try {
      if (this.writeSem.tryAcquire()) {
        this.mode = Mode.READABLE;
      }
      this.readSem.acquire();
    } catch (InterruptedException e) {
      System.out.println(e);
    }
    // System.out.println(Thread.currentThread().getName() + " acquired read lock, read: "+this.readSem.availablePermits() + ", write: "+this.writeSem.availablePermits());
  }

  public void releaseReadLock() {
    this.readSem.release();
    if (this.readSem.availablePermits() == this.MAXREADS) {
      this.mode = Mode.CLOSED;
      this.writeSem.release();
    }
    // System.out.println(Thread.currentThread().getName() + " released read lock, read: "+this.readSem.availablePermits() + ", write: "+this.writeSem.availablePermits());
  }

  public void acquireWriteLock() {
    try {
      this.writeSem.acquire();
      this.readSem.acquire(this.MAXREADS);
      this.mode = Mode.READWRITEABLE;
    } catch (InterruptedException e) {
      System.out.println(e);
    }
    // System.out.println(Thread.currentThread().getName() + " acquired write lock, read: "+this.readSem.availablePermits() + ", write: "+this.writeSem.availablePermits());
  }

  public void releaseWriteLock() {
    this.mode = Mode.CLOSED;
    this.writeSem.release();
    this.readSem.release(this.MAXREADS);
    // System.out.println(Thread.currentThread().getName() + " released write lock, read: "+this.readSem.availablePermits() + ", write: "+this.writeSem.availablePermits());
  }

  public Mode getMode() {
    return this.mode;
  }
}