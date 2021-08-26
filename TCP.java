import java.util.Arrays;

import java.net.Socket;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import java.io.PrintWriter;
import java.io.BufferedOutputStream;

import java.io.IOException;
import java.net.UnknownHostException;
import java.io.FileWriter;

public class TCP {
    private static final int BUFFER_SIZE = 8192*32;
    
    private static final int BAD_ARGUMENTS = 1;
    private static final int BAD_HOST      = 100;
    private static final int BAD_PROGRAM   = 200;
    private static final int BAD_SOCKET    = 300;
    private static final int BAD_BOT       = 400;
    private static final int UNEXPECTED    = 500;
    
    private static          boolean debug      = false;
    private static          int     exitCode   = 0;
    private static volatile boolean exiting    = false;
    private static volatile String  exitReason = null;
    	
    private static final String time() {
        final long time = System.nanoTime();
        return String.format("[%d.%010d]", time/1000000000L, time%1000000000L);
    }
    
    private static final void infoMessage(final String message) {
        if (debug) System.out.printf("\n%s >> %s", time(), message);
        else       System.out.printf("\n>> %s\t\t",       message);
    }
    
    private static final void errorHandler(final int code, final String message) {
        if (!exiting) {
            exiting = true;
            exitCode = code;
            exitReason = "of error";
            
            if (debug) DebugMsg(time() + " [ERROR] Sorry, I " + message); 
            else       DebugMsg(   "[ERROR] Sorry, I " + message);
        }
    }
    
    private static final void errorHandler(final int code, final String message, final Throwable e) {
        errorHandler(code, String.format("%s (%s)", message, e));
    }
    
	private static final void DebugMsg(String msg)
	{
//		try {
//			PrintWriter dbgpw = new PrintWriter(new FileWriter("tcp_debug.txt", true));
	//		dbgpw.println(msg);
		//	dbgpw.close();	
//		}
//		catch (IOException ignore) {
//		}
	}
	
    private static final void serverMessage(final String message) {
        if (debug) System.out.printf("\n%s    IN  | %s", time(), message);
//        else if ("go".equals(message)) System.out.print ("I");
		
		DebugMsg("OUTPUT: " + message);
		System.err.println(message);
    }
    
    private static final void botMessage(final int turn, final String message) {
        if (debug)                     System.out.printf("\n%s    OUT | %s",  time(), message);
//        else if ("go".equals(message)) System.out.print ("O");
//        else                           System.out.printf("\n  Turn %3d: %s\t", turn, message);
    }

	static class Timer extends Thread
	{
		/** Rate at which timer is checked */
		protected int m_rate = 100;

		/** Length of timeout */
		private int m_length;

		/** Time elapsed */
		private int m_elapsed;

		/**
		  * Creates a timer of a specified length
		  * @param	length	Length of time before timeout occurs
		  */
		public Timer ( int length )
		{
			// Assign to member variable
			m_length = length;

			// Set time elapsed
			m_elapsed = 0;
		}


		/** Resets the timer back to zero */
		public synchronized void reset()
		{
			m_elapsed = 0;
		}

		/** Performs timer specific code */
		public void run()
		{
			// Keep looping
			for (;;)
			{
				// Put the timer to sleep
				try
				{
					Thread.sleep(m_rate);
				}
				catch (InterruptedException ioe)
				{
					continue;
				}

				// Use 'synchronized' to prevent conflicts
				synchronized ( this )
				{
					// Increment time remaining
					m_elapsed += m_rate;

					// Check to see if the time has been exceeded
					if (m_elapsed > m_length)
					{
						// Trigger a timeout
						timeout();
					}
				}

			}
		}

		// Override this to provide custom functionality
		public void timeout()
		{
			System.err.println ("ERROR");
			System.exit(1);
		}
	}

	public static boolean m_bWaitingForNet = true;
    private static final class BotInputHandler implements Runnable {
        private final BufferedReader socket;
        private final PrintWriter    bot;
        
        BotInputHandler(final BufferedReader socket, final PrintWriter bot) {
            this.socket = socket;
            this.bot    = bot;
        }
        
        public final void run() {
            String line;
            try {
                while(true) {
					Timer t = new Timer(10000);
					t.start();
					line = socket.readLine();
					t.stop();

					if (line == null) break;

                    if (line.startsWith("INFO ")) {
                        infoMessage(line.substring(5));
                    }
                    else {
						if (line.equals("go"))
						{
							TCP.m_bWaitingForNet = false;
						}
                        bot.println(line);
                        bot.flush();
                        serverMessage(line);
                    }
                }
            }
            catch (IOException e) {
                errorHandler(BAD_SOCKET, "could not receive information from the server", e);
            }
            catch (Throwable e) {
                errorHandler(UNEXPECTED, "made an unexpected error", e);
                e.printStackTrace();
            }
            finally {
                if (!exiting) {
                    exiting = true;
                    exitReason = "server closed connection";
                }
                try { socket.close(); } catch (IOException e) {}
                bot.close();
            }
        }
    }
    
    private static final class BotOutputHandler implements Runnable {
        private final BufferedReader bot;
        private final PrintWriter    socket;
        
        BotOutputHandler(final BufferedReader bot, final PrintWriter socket) {
            this.bot    = bot;
            this.socket = socket;
        }
        
        public final void run() {
            int turn = 0;
            
            String line;
            try {
                while((line = bot.readLine()) != null) {
					socket.println(line);
                    if ("go".equals(line)) {
                        socket.flush();
						TCP.m_bWaitingForNet = true;						
                        turn++;
                    }
                    botMessage(turn, line);
                }
            }
            catch (IOException e) {
                errorHandler(BAD_BOT, "could not receive information from the bot", e);
            }
            catch (Throwable e) {
                errorHandler(UNEXPECTED, "made an unexpected error", e);
                e.printStackTrace();
            }
            finally {
                if (!exiting) {
                    exiting = true;
                    exitReason = "bot program ended";
                }
                try { bot.close(); } catch (IOException e) {}
                socket.close();
            }
        }
    }
    
    public static void main(String args[]) {
        System.setProperty("line.separator", "\n");
		if (args.length < 4 || (args.length == 4 && "-d".equals(args[0]))) {
            System.err.println("PlanetWars tester TCP edition");
            System.err.println("  USAGE:   java -server TCP [-d] [host]            [port]  [username] [password]  [bot command line]");
            System.err.println("  EXAMPLE: java -server TCP      www.benzedrine.cx 9999    javaexample asdsd java MyBot");
            System.err.println();
            System.err.println("  -d\tturns on debug mode, which reveals what the server and bot send to each other.");
            System.exit(BAD_ARGUMENTS);
        }
        
        Process bot    = null;
        Socket  socket = null;
        
        try {
            final String   address;
            final int      port;
            final String   username;
            final String[] command;
			final String 	password;
            
            if ("-d".equals(args[0])) {
                debug    = true;
                address  = args[1];
                port     = Integer.parseInt(args[2]);
                username = args[3];
				password = args[4];
                command  = Arrays.copyOfRange(args, 5, args.length);				
            }
            else {
                debug    = false;
                address  = args[0];
                port     = Integer.parseInt(args[1]);
                username = args[2];
				password = args[3];
                command  = Arrays.copyOfRange(args, 4, args.length);
            }
            
            infoMessage(String.format("Starting %s", Arrays.toString(command)));
                                 bot    = Runtime.getRuntime().exec(command);
            final BufferedReader botIn  = new BufferedReader(new InputStreamReader(bot.getInputStream()), BUFFER_SIZE);
            final PrintWriter    botOut = new PrintWriter(bot.getOutputStream(), true);
            
            infoMessage(String.format("Connecting to Planet Wars server at %s:%d as %s", address, port, username));
                                 socket    = new Socket(address, port);
            final BufferedReader socketIn  = new BufferedReader(new InputStreamReader(socket.getInputStream()), BUFFER_SIZE);
            final PrintWriter    socketOut = new PrintWriter(new BufferedOutputStream(socket.getOutputStream(), BUFFER_SIZE));
            
            socketOut.print("USER ");
            socketOut.print(username);
            socketOut.print(" PASS ");
			socketOut.println(password);
            socketOut.flush();

            final Thread botInputHandler  = new Thread(new BotInputHandler (socketIn, botOut), "Bot input handler");
            botInputHandler.start();
            final Thread botOutputHandler = new Thread(new BotOutputHandler(botIn, socketOut), "Bot output handler");
            botOutputHandler.start();
            
            botInputHandler.join();
            botOutputHandler.join();
        }
        catch (UnknownHostException e) {
            errorHandler(BAD_HOST, "could not find a server with that name");
        }
        catch (IOException e) {
            if (e.getMessage().startsWith("Cannot run program")) {
                errorHandler(BAD_PROGRAM, "could not run that bot program");
            }
            else {
                errorHandler(UNEXPECTED, "made an unexpected error", e);
                e.printStackTrace();
            }
        }
        catch (Throwable e) {
            errorHandler(UNEXPECTED, "made an unexpected error", e);
            e.printStackTrace();
        }
        finally {
            if (socket != null) try { socket.close(); } catch (IOException e) {}
            if (bot    != null) bot.destroy();
            if (exitReason != null) infoMessage(String.format("Exiting because %s", exitReason));
            System.exit(exitCode);
        }
    }
}