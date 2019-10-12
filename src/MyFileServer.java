import java.util.HashMap;
import java.util.Optional;
import java.util.Set;

/**
 * The idea for this FileServer implementation is that the file mode can be
 * inferred by the status of it's associated locks.
 * 
 * As a result, there will be 2 HashMaps:
 * 1. HashMap for the filename and file content
 * 2. HashMap for the filename and it's read and write locks.
 * 
 * The Read and Write lock is implemented in ReadWriteSemLock.java, read the docstring there
 * In terms of fairness, the processes get allocated read or write locks
 * in the order they were added to the queue. (LIFO).
 * To avoid race conditions, essentially I allow up to 10 concurrent read accesses (whilst blocking write access)
 * and I allow max 1 concurrent write access (while blocking read access)
 * Utilising the ReadWriteSemLock I implemented
 * 
 * These numbers above are per unique file. EG if someone wanted to concurrently write
 * to 3 different files, that's fine, since the lock is per file.
 * 
 * Starvation is avoided by always releasing locks once the file has been 
 * 
 * @author jamesjarvis
 */

public class MyFileServer implements FileServer {

  private HashMap<String, String> files;
  private HashMap<String, ReadWriteSemLock> locks;

  public MyFileServer() {
    this.files = new HashMap<String, String>();
    this.locks = new HashMap<String, ReadWriteSemLock>();
  }

  @Override
  public void create(String filename, String content) {
    this.locks.put(filename, new ReadWriteSemLock());
    this.files.put(filename, content);
  }

  @Override
  public Optional<File> open(String filename, Mode mode) {
    if (!this.files.containsKey(filename)) {
      return Optional.empty();
    }
    try {
      switch (mode) {
      case READABLE:
        this.locks.get(filename).acquireReadLock();
        // System.out.println(Thread.currentThread().getName() + " acquired read lock on " + filename);
        break;
      case READWRITEABLE:
        this.locks.get(filename).acquireWriteLock();
        // System.out.println(Thread.currentThread().getName() + " acquired write lock on " + filename);
        break;
      default:
        return Optional.empty();
      }
    } catch (Exception e) {
      System.err.println(e);
    }

    String acquiredFileContents = this.files.get(filename);
    return Optional.of(new File(filename, acquiredFileContents, mode));
  }

  @Override
  public void close(File file) {
    // System.out.println(Thread.currentThread().getName() + " Recieved close request: " + file.filename() + file.mode() + file.read()); 
    Mode actualMode = this.locks.get(file.filename()).getMode();
    try {
      switch (actualMode) {
      case READABLE:
        if (file.mode() == Mode.READABLE) {
          this.locks.get(file.filename()).releaseReadLock();
          // System.out.println(Thread.currentThread().getName() + " released read lock on " + file.filename());
        }
        break;
      case READWRITEABLE:
        if (file.mode() == Mode.READWRITEABLE) {
          this.files.put(file.filename(), file.read());
          this.locks.get(file.filename()).releaseWriteLock();
          // System.out.println(Thread.currentThread().getName() + " released write lock on " + file.filename()+", wrote "+file.read());
        }
        break;
      default:
        break;
      }
    } catch (Exception e) {
      System.out.println(e);
    }
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