// CO661 - Theory and Practice of Concurrency
// School of Computing, University of Kent
// Dominic Orchard & Laura Bocchi 2018-2020

// For storing file information on the server (contains the content and the mode)
class FileFrame {
	public String content;
	public Mode mode;

	public FileFrame(String content, Mode mode) {
		this.content = content;
		this.mode = mode;
	}
}