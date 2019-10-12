import java.util.concurrent.ThreadLocalRandom;
import java.util.Optional;

/**
 * Client implemented to show that the client-server interactions work as intended.
 */
public class Client implements Runnable {

  private MyFileServer fs;

  public Client(MyFileServer fs) {
    this.fs = fs;
  }

  public void run() {
    // Will go through and perform 10 random operations on a random one of the files
    for (int i = 0; i < 10; i++) {
      String filename = ThreadLocalRandom.current().nextInt(1, 5) + ".txt";
      boolean isWrite = ThreadLocalRandom.current().nextBoolean();
      Mode mode = isWrite ? Mode.READWRITEABLE : Mode.READABLE;
      printinfo("ATTEMPT OPEN -", filename, mode, "");
      Optional<File> of = fs.open(filename, Mode.READWRITEABLE);
      File f = of.get();
      printinfo("OPENED -------", filename, mode, "");
      String temp = f.read();
      printinfo("READ ---------", filename, mode, temp);
      if (isWrite) {
        String tempWrite = temp + temp.substring(0, 1);
        f.write(tempWrite);
        printinfo("WROTE --------", filename, mode, tempWrite);
      }
      fs.close(f);
      printinfo("CLOSED -------", filename, mode, "");
    }
  }

  public void printinfo(String info, String filename, Mode mode, String content) {
    System.out.println(Thread.currentThread().getName() +" "+ mode+ " " + info + " from "+ filename +" : "+ content);
  }

  public static void main(String[] args) {

    // Set up file server with 5 initial files
    MyFileServer fs = new MyFileServer();
    fs.create("1.txt", "A");
    fs.create("2.txt", "B");
    fs.create("3.txt", "C");
    fs.create("4.txt", "D");
    fs.create("5.txt", "E");

    // Set up 10 threads to do their 10 random operations ... 100 operations in total
    for (int i = 0; i < 10; i++) {
      Thread client = new Thread(new Client(fs));
      client.start();
    }
  }
}