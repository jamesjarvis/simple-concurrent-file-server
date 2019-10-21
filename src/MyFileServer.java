import java.util.HashMap;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.Semaphore;

/**
 * The idea for this FileServer implementation is that the file mode can be
 * inferred by the status of it's associated locks.
 * 
 * As a result, there will be 2 HashMaps: 1. HashMap for the filename and file
 * content 2. HashMap for the filename and it's read and write locks.
 * 
 * The Read and Write lock is implemented in ReadWriteLocker, read the docstring
 * there In terms of fairness, the processes get allocated read or write locks
 * in the order they were added to the queue. (LIFO). To avoid race conditions,
 * essentially I allow up to N concurrent read accesses (whilst blocking write
 * access) and I allow max 1 concurrent write access (while blocking read
 * access) Utilising the ReadWriteLocker I implemented
 * 
 * These numbers above are per unique file. EG if someone wanted to concurrently
 * write to 3 different files, that's fine, since the lock is per file.
 * 
 * Starvation is avoided by always releasing locks once the file has been
 * 
 * @author jamesjarvis
 */

public class MyFileServer implements FileServer {

  private HashMap<String, FileFrame> files;
  private HashMap<String, ReadWriteLocker> locks;

  public MyFileServer() {
    this.files = new HashMap<String, FileFrame>();
    this.locks = new HashMap<String, ReadWriteLocker>();
  }

  @Override
  public void create(String filename, String content) {
    this.locks.put(filename, new ReadWriteLocker());
    this.files.put(filename, new FileFrame(content, Mode.CLOSED));
  }

  @Override
  public Optional<File> open(String filename, Mode mode) {
    if (!this.files.containsKey(filename)) {
      return Optional.empty();
    }
    try {
      switch (mode) {
      case READABLE:
        this.locks.get(filename).readLock();
        break;
      case READWRITEABLE:
        this.locks.get(filename).writeLock();
        break;
      default:
        return Optional.empty();
      }
    } catch (Exception e) {
      System.err.println(e);
    }

    FileFrame acquired = this.files.get(filename);
    acquired.mode = mode;
    return Optional.of(new File(filename, acquired.content, acquired.mode));
  }

  @Override
  public void close(File file) {
    Mode actualMode = this.locks.get(file.filename()).getMode();
    Mode fileMode = file.mode();
    if (fileMode != actualMode) {
      return;
    }
    FileFrame current = this.files.get(file.filename());

    if (fileMode == Mode.READABLE) {
      this.locks.get(file.filename()).readUnlock();
    } else if (fileMode == Mode.READWRITEABLE) {
      current.content = file.read();
      this.locks.get(file.filename()).writeUnlock();
    }

    current.mode = this.locks.get(file.filename()).getMode();

  }

  @Override
  public Mode fileStatus(String filename) {
    if (!files.containsKey(filename)) {
      return Mode.UNKNOWN;
    }
    return this.locks.get(filename).getMode();
  }

  @Override
  public Set<String> availableFiles() {
    return this.files.keySet();
  }
}

/**
 * ReadWriteLocker features two semaphores, readSem and writeSem
 * 
 * readSem allows for a specified maximum concurrent read requests, and
 * writeSem, only 1 write request.
 * 
 * This is due to the fact that there is a specified max buffer for the read
 * semaphore. Also, if the read is starting from a closed state, it also obtains
 * the write lock to prevent any writes while being read.
 * 
 * Similarly, when the write lock is being obtained (it can only be obtained if
 * all write and read locks are available), then it also allocated all of the
 * read locks in order to prevent any reads while being written to.
 */
class ReadWriteLocker {
  private int reads;
  private boolean isWrite;
  private ReentrantLock locker;
  private Semaphore readSem;
  private Semaphore writeSem;
  private int MAXREADS;

  public ReadWriteLocker() {
    this.MAXREADS = 10000;
    this.reads = 0;
    this.isWrite = false;
    this.locker = new ReentrantLock(true);
    this.readSem = new Semaphore(MAXREADS);
    this.writeSem = new Semaphore(1);
  }

  public int getReads() {
    this.locker.lock();

    try {
      return this.reads;
    } finally {
      this.locker.unlock();
    }
  }

  public boolean isWrite() {
    this.locker.lock();

    try {
      return this.isWrite;
    } finally {
      this.locker.unlock();
    }
  }

  /**
   * Read lock prevents writes and adds a read to the count
   */
  public void readLock() {

    try {
      this.readSem.acquire();
    } catch (InterruptedException e) {
      System.out.println(e);
    }

    this.locker.lock();

    try {
      this.reads += 1;
    } finally {
      this.locker.unlock();
    }
  }

  public void writeLock() {

    try {
      this.readSem.acquire(this.MAXREADS);
      this.writeSem.acquire();
    } catch (InterruptedException e) {
      System.out.println(e);
    }

    this.locker.lock();

    try {
      this.isWrite = true;
    } finally {
      this.locker.unlock();
    }
  }

  public void readUnlock() {
    this.locker.lock();

    try {
      this.reads--;
      if (this.reads == 0) {
        this.writeSem.release();
      }
      this.readSem.release();

    } finally {
      this.locker.unlock();
    }
  }

  public void writeUnlock() {
    this.locker.lock();

    try {
      this.isWrite = false;
      this.writeSem.release();
      this.readSem.release(this.MAXREADS);

    } finally {
      this.locker.unlock();
    }
  }

  public Mode getMode() {
    this.locker.lock();

    try {
      if (this.isWrite) {
        return Mode.READWRITEABLE;
      } else if (this.reads > 0) {
        return Mode.READABLE;
      }
      return Mode.CLOSED;
    } finally {
      this.locker.unlock();
    }
  }
}