using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.Data;
using System.Drawing;
using System.Linq;
using System.Text;
using System.Windows.Forms;
using System.Diagnostics;
using System.Threading;
using System.IO;
using System.Net;
using System.Net.Sockets;

namespace BotBenchmark
{
	public partial class GameVis : Form
	{
		private String m_Executable, m_Args;
		private List<Fleet> fleets = new List<Fleet>();
		private List<Planet> planets = new List<Planet>();

		private List<String> history = new List<string>();

		private Queue<String> commands = new Queue<string>();
		private Thread m_InputThread;
		private Thread m_DebugThread;

		private Queue<String> m_TaskOutput = new Queue<string>();
		private Process m_Process;
		private int m_TurnNumber = 0;
		private bool m_bWaitForDebug = true;
		private Dictionary<int, String> m_DebugHistory = new Dictionary<int,string>();
		private String m_PlayBackFile, m_DebugFile;
        private bool m_PlayBackOldFmt;

        public bool PlayBackOldFmt
        {
            get { return m_PlayBackOldFmt; }
            set { m_PlayBackOldFmt = value; }
        }

		private int m_GameResult = -1;
		private bool m_bDoneInput;
		private Socket m_DebugServerSocket;
        private String m_RecordFile, m_RecordDebugFile, m_RecordFileOld, m_RecordDebugFileOld;
        private bool m_bRecordFileChanged = false, m_bRecordDebugFileChanged = false;

        private PlanetWarsDebugger pwd;
        private String m_ShowDistance = "";

		private MainForm m_Parent;
        private Process m_BotProcess;

		private bool m_bCloseWhenDone = false;

        public int TurnNumber { get { return m_TurnNumber; } }

		public GameVis(MainForm mf, String proc, String args, bool closeWhenDone)
		{
			InitializeComponent();
			m_Executable = proc;
			m_Args = args;
			m_bCloseWhenDone = closeWhenDone;
			m_Parent = mf;

            pwd = new PlanetWarsDebugger(planets);            
		}

		public GameVis(MainForm mf, String playbackfile)
		{
			InitializeComponent();
			m_PlayBackFile = playbackfile;
			m_PlayBackOldFmt = playbackfile.Contains("map");
			m_DebugFile = playbackfile.Replace(".txt", "_dbg.txt");
			m_Parent = mf;

            pwd = new PlanetWarsDebugger(planets);
        }

        public int MyPlayerID { get { return (int)numericUpDown1.Value; } set { numericUpDown1.Value = value; } }

        public void EnableRecording(String file)
        {
            m_RecordFile = file;
            m_RecordDebugFile = file.Replace(".txt", "_dbg.txt");
        }

		private void GameVis_Load(object sender, EventArgs e)
		{
			this.Location = new Point(	int.Parse(BotBenchmark.MainForm.CONFIG["vis.x"]), 
										int.Parse(BotBenchmark.MainForm.CONFIG["vis.y"]));

			this.Size = new Size( int.Parse(BotBenchmark.MainForm.CONFIG["vis.width"]), 
								  int.Parse(BotBenchmark.MainForm.CONFIG["vis.height"]));

			this.WindowState = (FormWindowState)Enum.Parse(typeof(FormWindowState), BotBenchmark.MainForm.CONFIG["vis.state"]);

			if (m_PlayBackFile == null)
			{
				button1.Enabled = button2.Enabled = button3.Enabled = button4.Enabled = button5.Enabled = false;
				timer1.Enabled = true;
			}

			m_InputThread = new Thread(new ThreadStart(InputThread));
			m_InputThread.Start();

			m_DebugThread = new Thread(new ThreadStart(DebugThread));
			m_DebugThread.Start();
		}

		private void OutputReceived(object sender, DataReceivedEventArgs e)
		{
			lock (this)
			{
				if (e.Data != null)
					m_TaskOutput.Enqueue(e.Data);
			}
		}

		public void DebugConnectionReceived(IAsyncResult iar)
		{
			if (!m_bDoneInput)
			{
				Socket server = (Socket)iar.AsyncState;
				Socket client = server.EndAccept(iar);

				m_bWaitForDebug = true;

				StreamReader csr = new StreamReader(new NetworkStream(client));
				DebugReadStream(csr);

				client.Close();
				server.Close();
			}
		}

		public void DebugThread()
		{
			StreamReader csr = null;

			m_bWaitForDebug = false;

			Socket clientSoc = null;
			if (m_PlayBackFile == null)
			{
				m_DebugServerSocket = new Socket(AddressFamily.InterNetwork, SocketType.Stream, ProtocolType.Tcp);
				IPEndPoint ipLocal = new IPEndPoint(IPAddress.Any, 10001);
				m_DebugServerSocket.Bind(ipLocal);
				m_DebugServerSocket.Listen(4);

				m_DebugServerSocket.BeginAccept(new AsyncCallback(DebugConnectionReceived), m_DebugServerSocket);
			}
			else
			{
				try
				{
					csr = File.OpenText(m_DebugFile);
					m_bWaitForDebug = true;
					DebugReadStream(csr);
				}
				catch (Exception)
				{
					return;
				}
			}
		}

		private void DebugReadStream(StreamReader csr)
		{
			String line = null;
			String debugMsg = "";
			int currentTurn = -1;
			try
			{
				while ((line = csr.ReadLine()) != null)
				{
					if (line.StartsWith("TURN: "))
					{
						if (currentTurn > -1)
						{
							lock (this)
							{
								m_DebugHistory.Add(currentTurn, debugMsg);
							}
                            try
                            {
                                lock (this)
                                {
                                    if (m_bRecordDebugFileChanged && File.Exists(m_RecordDebugFileOld))
                                    {
                                        File.Move(m_RecordDebugFileOld, m_RecordDebugFile);
                                        m_bRecordDebugFileChanged = false;
                                    }
                                    File.AppendAllText(m_RecordDebugFile, "TURN: " + currentTurn + "\n" + debugMsg);
                                }
                            }
                            catch (Exception) { }
						}
						debugMsg = "";
						currentTurn = int.Parse(line.Replace("TURN: ", ""));
					}
					else
					{
						debugMsg += line + "\n";
					}
				}
			}
			catch (SocketException se)
			{
				m_TaskOutput.Enqueue(se.Message + "\n");
				m_bWaitForDebug = false;
			}

			lock (this)
			{
				m_DebugHistory.Add(currentTurn, debugMsg);

                try
                {
                    File.AppendAllText(m_RecordDebugFile, "TURN: " + currentTurn + "\n" + debugMsg);
                }
                catch (Exception)
                {
                }
			}
		}

		public void InputThread()
		{
			StreamReader sr = null;

			if (m_PlayBackFile == null)
			{
				ProcessStartInfo psi = new ProcessStartInfo(m_Executable, m_Args);
				psi.UseShellExecute = false;
				psi.CreateNoWindow = true;
				psi.RedirectStandardError = true;
				psi.RedirectStandardOutput = true;

				lock (this)
				{
					m_Process = Process.Start(psi);
				}
				Process p = m_Process;
				p.OutputDataReceived += new DataReceivedEventHandler(OutputReceived);
				p.BeginOutputReadLine();

				sr = p.StandardError;
			}
			else
			{
				sr = File.OpenText(m_PlayBackFile);
			}
//			StreamReader sr = File.OpenText("tcp_log.txt");

			m_bDoneInput = false;
			//p.WaitForExit();
			String line = "", cmd = "";
			while ((line = sr.ReadLine()) != null)
			{
				if (m_PlayBackOldFmt)
				{
					int idxBar = line.IndexOf("|");
					if (idxBar > -1)
					{
						cmd += line.Substring(0, idxBar);
						line = line.Substring(idxBar + 1);
						SetPlanets(cmd);
                        String firstTurn = "";
                        foreach (Planet p in planets)
                        {
                            firstTurn += p.Owner() + "." + p.NumShips() + ",";
                        }
                        history.Add(firstTurn.Substring(0, firstTurn.Length - 1));
						cmd = "";
					}

					while (line.Length > 0)
					{
						int idxColon = line.IndexOf(":");
						if (idxColon > -1)
						{
							cmd += line.Substring(0, idxColon);
							lock (this)
							{
								commands.Enqueue(cmd);
								history.Add(cmd);
							}
							cmd = "";
							line = line.Substring(idxColon+1);
						}
						else
						{
							cmd += line;
							line = "";
						}
					}
				}
				else
				{
					if (line == "go")
					{
						lock (this)
						{
                            commands.Enqueue(cmd);
							history.Add(cmd);
						}

                        if (m_RecordFile != null)
                        {
                            lock (this) {
                                if (m_bRecordFileChanged && File.Exists(m_RecordFileOld))
                                {
                                    File.Move(m_RecordFileOld, m_RecordFile);
                                    m_bRecordFileChanged = false;
                                }
                                File.AppendAllText(m_RecordFile, cmd + "go\n");
                            }
                        }
						cmd = "";
					}
					else
					{
						cmd += line + "\n";
					}
				}
			}
//			commands.Enqueue(cmd);

			m_bDoneInput = true;
			sr.Close();
		}

		public void SetPlanets(String cmd)
		{
			String[] plist = cmd.Split(':');
			planets.Clear();
			int planetID = 0;
			foreach (String pi in plist)
			{
				String[] tokens = pi.Split(',');
				double x = Double.Parse(tokens[0]);
				double y = Double.Parse(tokens[1]);
				int owner = int.Parse(tokens[2]);
				int numShips = int.Parse(tokens[3]);
				int growthRate = int.Parse(tokens[4]);
				Planet p = new Planet(planetID++,
						owner,
						numShips,
						growthRate,
						x, y);

				planets.Add(p);
			}

            pwd.UpdatePlanets();
		}

		public void ProcessCommand(String s)
		{
			planets.Clear();
			fleets.Clear();

			int planetID = 0;
			String[] lines = s.Split('\n');
			for (int i = 0; i < lines.Length; ++i)
			{
				String line = lines[i];
				int commentBegin = line.IndexOf('#');
				if (commentBegin >= 0)
				{
					line = line.Substring(0, commentBegin);
				}
				if (line.Trim().Length == 0)
				{
					continue;
				}
				String[] tokens = line.Split(' ');
				if (tokens.Length == 0)
				{
					continue;
				}
				if (tokens[0] == "P")
				{
					if (tokens.Length != 6)
					{
						return;
					}
					double x = Double.Parse(tokens[1]);
					double y = Double.Parse(tokens[2]);
					int owner = int.Parse(tokens[3]);
					int numShips = int.Parse(tokens[4]);
					int growthRate = int.Parse(tokens[5]);
					Planet p = new Planet(planetID++,
							owner,
							numShips,
							growthRate,
							x, y);
					planets.Add(p);
				}
				else if (tokens[0] == "F")
				{
					if (tokens.Length != 7)
					{
						return;
					}
					int owner = int.Parse(tokens[1]);
					int numShips = int.Parse(tokens[2]);
					int source = int.Parse(tokens[3]);
					int destination = int.Parse(tokens[4]);
					int totalTripLength = int.Parse(tokens[5]);
					int turnsRemaining = int.Parse(tokens[6]);
					Fleet f = new Fleet(owner,
							numShips,
							source,
							destination,
							totalTripLength,
							turnsRemaining);
					fleets.Add(f);
				}
				else
				{
					continue;
				}
			}

            pwd.UpdatePlanets();
		}

		private void ProcessOldCommand(String s)
		{
			if (s.Trim().Length == 0) return;

			String[] parts = s.Split(',');
			int idx = 0;
			foreach (Planet p in planets)
			{
				String [] pi = parts[idx].Split('.');
				p.Owner(int.Parse(pi[0]));
				p.NumShips(int.Parse(pi[1]));
				idx++;
			}

			fleets.Clear();
			for (int i = idx; i < parts.Length; ++i)
			{
				String[] tokens = parts[i].Split('.');
				int owner = int.Parse(tokens[0]);
				int numShips = int.Parse(tokens[1]);
				int source = int.Parse(tokens[2]);
				int destination = int.Parse(tokens[3]);
				int totalTripLength = int.Parse(tokens[4]);
				int turnsRemaining = int.Parse(tokens[5]);
				Fleet f = new Fleet(owner,
						numShips,
						source,
						destination,
						totalTripLength,
						turnsRemaining);
				fleets.Add(f);
			}
		}

   

		private void ProcessDebugInfo(String info)
		{
            Dictionary<String, String> debugInfo = new Dictionary<string, string>();

            String[] lines = info.Split('\n');

            String section = null, sectionData = "";
			foreach (String rawline in lines)
			{
                String line = rawline.Trim();
                if (line.Trim().Length == 0) continue;

                if (line.StartsWith("[") && line.EndsWith("]"))
                {
                    if (section != null)
                    {
                        debugInfo.Add(section, sectionData);
                    }
                    section = line.Substring(1, line.Length - 2);
                    sectionData = "";
                }
                else
                {
                    sectionData += line + "\n";
                }
			}
            if (section != null)
            {
                debugInfo.Add(section, sectionData);
            }

            pwd.ProcessDebugSections(m_TurnNumber, debugInfo);
		}

        void ClearDebugInfo()
        {
            pwd.ClearDebugInfo();
        }

		private void ComputeDrawBoundaries(ref double maxX, ref double minX, ref double maxY, ref double minY, ref double scaleX, ref double scaleY)
		{
			maxX = -10000; minX = 10000; maxY = -10000; minY = 10000;

            lock (this)
            {
                foreach (Planet p in planets)
                {
                    if (p.X() > maxX) maxX = p.X();
                    if (p.Y() > maxY) maxY = p.Y();
                    if (p.X() < minX) minX = p.X();
                    if (p.Y() < minY) minY = p.Y();
                }
            }

			double expand = 4;
			maxX += expand; maxY += expand; minY -= expand; minX -= expand;
			scaleX = pictureBox1.Width / (maxX - minX);
			scaleY = pictureBox1.Height / (maxY - minY);
		}

		private void pictureBox1_Paint(object sender, PaintEventArgs e)
		{
			e.Graphics.InterpolationMode = System.Drawing.Drawing2D.InterpolationMode.HighQualityBicubic;
			e.Graphics.SmoothingMode = System.Drawing.Drawing2D.SmoothingMode.HighQuality;
			e.Graphics.TextRenderingHint = System.Drawing.Text.TextRenderingHint.AntiAlias;

			double maxX = -10000, minX = 10000, maxY = -10000, minY = 10000, scaleX = 0, scaleY = 0;
			ComputeDrawBoundaries(ref maxX, ref minX, ref maxY, ref minY, ref scaleX, ref scaleY);

            pwd.SetDrawBounderies(minX, maxX, minY, maxY, scaleX, scaleY);

			e.Graphics.Clear(Color.Black);

			Font fnt = new Font(FontFamily.GenericSansSerif, 8);
			Font fntp = new Font(FontFamily.GenericSansSerif, 10, FontStyle.Bold);

			if (cbGraph.Checked) {
                pwd.RenderGlobalDebug(e.Graphics, pictureBox1.Width, pictureBox1.Height);
			}

            lock (this)
            {
                foreach (Planet p in planets)
                {
                    pwd.RenderPlanet(p, e.Graphics, pictureBox1.Width, pictureBox1.Height);
                }

                foreach (Fleet f in fleets)
                {
                    Planet dp = planets.Find(p => p.PlanetID() == f.DestinationPlanet());
                    Planet sp = planets.Find(p => p.PlanetID() == f.SourcePlanet());

                    float fact = 1.0f - f.TurnsRemaining() / (float)f.TotalTripLength();

                    float dx = (float)(dp.X() - sp.X());
                    float dy = (float)(dp.Y() - sp.Y());

                    float x = dx * fact + (float)sp.X();
                    float y = dy * fact + (float)sp.Y();

                    x = (float)((x - minX) * scaleX);
                    y = pictureBox1.Height - (float)((y - minY) * scaleY);

                    float norm = (float)Math.Sqrt(dx * dx + dy * dy);
                    dx /= norm; dy /= norm;
                    float len = 7;
                    float x2 = x - dx * len, y2 = y + dy * len;

                    Brush b = f.Owner() == numericUpDown1.Value ? Brushes.Red : Brushes.LightBlue;

                    Point[] arr = new Point[] 
				    {	
					    new Point((int)x, (int)y), 
					    new Point((int)(x2 - dy * len / 3), (int)(y2 - dx * len / 3)), 
					    new Point((int)(x2 + dy * len / 3), (int)(y2 + dx * len / 3)) 
				    };

                    e.Graphics.FillPolygon(b, arr);

                    e.Graphics.DrawString(f.NumShips() + "", fnt, b, x, y);

                }
            }

            e.Graphics.DrawString(m_TurnNumber + "", fntp, Brushes.White, 0, pictureBox1.Height - 12);
            e.Graphics.DrawString(m_ShowDistance, fntp, Brushes.White, pictureBox1.Width - 50, pictureBox1.Height - 12);            
		}

		private void timer1_Tick(object sender, EventArgs e)
		{
			String command = null;
			lock (this)
			{
				while (m_TaskOutput.Count > 0)
				{
					String s = m_TaskOutput.Dequeue();
					textBox1.Text += s.Replace("\n", "\r\n") + "\r\n";
					if (textBox1.Text.Length > 0)
					{
						textBox1.SelectionStart = textBox1.Text.Length - 1;
						textBox1.ScrollToCaret();
					}

					if (s.Contains("LOSE"))
						m_GameResult = 0;
					if (s.Contains("WIN"))
						m_GameResult = 1;
                    if (s.Contains("ERROR"))
                        m_GameResult = -1;

                    if (s.Contains("opponent"))
                    {
                        s = s.Replace(">> Your opponent is", "");
                        s = s.Replace("with ", "");
                        s = s.Replace(" Elo", "");
                        s = s.Trim();
                        String[] parts = s.Split(' ');
                        lock (this)
                        {
                            m_RecordFileOld = m_RecordFile;
                            m_RecordDebugFileOld = m_RecordDebugFile;
                            if (parts.Length == 2)
                                m_RecordFile = Path.GetDirectoryName(m_RecordFile) + "/" + Path.GetFileNameWithoutExtension(m_RecordFile) + "_" + parts[0] + "_" + parts[1] + ".txt";
                            else
                                m_RecordFile = Path.GetDirectoryName(m_RecordFile) + "/" + Path.GetFileNameWithoutExtension(m_RecordFile) + "_" + s + ".txt";
                            m_RecordDebugFile = m_RecordFile.Replace(".txt", "_dbg.txt");
                            m_bRecordFileChanged = true;
                            m_bRecordDebugFileChanged = true;
                        }
                    }
				}

				if (commands.Count > 0)
				{
					bool debugReady = !m_bWaitForDebug || (m_bWaitForDebug && m_DebugHistory.ContainsKey(m_TurnNumber));
					if (debugReady)
					{
						command = commands.Dequeue();
						if (!m_bWaitForDebug)
                            m_TurnNumber++;
					}
				}
				else if (m_PlayBackFile != null)
				{
					timer1.Enabled = false;
				}
				else if (m_bCloseWhenDone && m_bDoneInput)
				{
					m_Parent.OnlineGameResult = m_GameResult;
					m_DebugServerSocket.Close();
					Close();
				}
			}
			if (command != null)
			{
				if (m_PlayBackFile != null && m_PlayBackOldFmt)
					ProcessOldCommand(command);
				else
					ProcessCommand(command);

                if (m_bWaitForDebug)
                {
                    ProcessDebugInfo(m_DebugHistory[m_TurnNumber]);
                    m_TurnNumber++;
                }
                else
                    ClearDebugInfo();

                pictureBox1.Refresh();
                pictureBox2.Refresh();
			}
		}

		private void SaveLayout()
		{
			BotBenchmark.MainForm.CONFIG["vis.x"] = this.Location.X.ToString();
			BotBenchmark.MainForm.CONFIG["vis.y"] = this.Location.Y.ToString();
			BotBenchmark.MainForm.CONFIG["vis.width"] = this.Width.ToString();
			BotBenchmark.MainForm.CONFIG["vis.height"] = this.Height.ToString();

			BotBenchmark.MainForm.CONFIG["vis.state"] = this.WindowState.ToString();
		}

		private void GameVis_FormClosing(object sender, FormClosingEventArgs e)
		{
            if (m_BotProcess != null)
                TerminateBot();

            bool bRet = false;
			lock (this)
			{
				if ( (m_Process != null && m_Process.HasExited) || (m_PlayBackFile != null && !timer1.Enabled) )
				{
					bRet = true;
				}
			}

			if (bRet)
			{
                m_Parent.OnlineGameResult = m_GameResult;
                m_InputThread.Join();
				if (m_DebugServerSocket != null)
					m_DebugServerSocket.Close();
				SaveLayout();
				return;
			}

			if (DialogResult.Yes == MessageBox.Show(this, "Are you sure you want to abandon this game?", "Confirm", MessageBoxButtons.YesNo, MessageBoxIcon.Question))
			{
				lock (this)
				{
					if (m_Process != null && !m_Process.HasExited)
						m_Process.Kill();

					m_bDoneInput = true;

					if (m_DebugServerSocket != null)
						m_DebugServerSocket.Close();

					m_InputThread.Join();
					m_DebugThread.Join();

                    m_Parent.OnlineGameResult = 0;
				}
			}
			else
				e.Cancel = true;

			SaveLayout();
		}

		private void GameVis_Resize(object sender, EventArgs e)
		{
			pictureBox1.Refresh();
            pictureBox2.Refresh();
		}

		private void splitContainer1_SplitterMoved(object sender, SplitterEventArgs e)
		{
			pictureBox1.Refresh();
            pictureBox2.Refresh();
		}

		private void button1_Click(object sender, EventArgs e)
		{
			timer1.Enabled = !timer1.Enabled;
		}

		private void button2_Click(object sender, EventArgs e)
		{
			timer1.Enabled = false;
			m_TurnNumber++;

			lock (this)
			{
				if (m_TurnNumber >= history.Count)
				{
					m_TurnNumber = history.Count;
					return;
				}
			}

			String command = history[m_TurnNumber];
			if (m_PlayBackFile != null && m_PlayBackOldFmt)
				ProcessOldCommand(command);
			else
				ProcessCommand(command);

            if (m_DebugHistory.ContainsKey(m_TurnNumber))
                ProcessDebugInfo(m_DebugHistory[m_TurnNumber]);
            else
                ClearDebugInfo();
            pictureBox1.Refresh();
            pictureBox2.Refresh();
        }

		private void button3_Click(object sender, EventArgs e)
		{
			timer1.Enabled = false;
			m_TurnNumber--;
			if (m_TurnNumber < 0)
			{
				m_TurnNumber = 0;
				return;
			}

			String command = history[m_TurnNumber];
			if (m_PlayBackFile != null && m_PlayBackOldFmt)
				ProcessOldCommand(command);
			else
				ProcessCommand(command);

            if (m_DebugHistory.ContainsKey(m_TurnNumber)) 
                ProcessDebugInfo(m_DebugHistory[m_TurnNumber]);
            else
                ClearDebugInfo();
            pictureBox1.Refresh();
            pictureBox2.Refresh();
		}

		private void button4_Click(object sender, EventArgs e)
		{
			timer1.Enabled = false;
			m_TurnNumber = 0;

			String command = history[m_TurnNumber];
			if (m_PlayBackFile != null && m_PlayBackOldFmt)
				ProcessOldCommand(command);
			else
				ProcessCommand(command);

            if (m_DebugHistory.ContainsKey(m_TurnNumber)) 
                ProcessDebugInfo(m_DebugHistory[m_TurnNumber]);
            else
                ClearDebugInfo();
            pictureBox1.Refresh();
            pictureBox2.Refresh();
        }

		private void pictureBox1_Click(object sender, EventArgs e)
		{

		}

		private void pictureBox1_MouseDown(object sender, MouseEventArgs e)
		{
			double maxX = -10000, minX = 10000, maxY = -10000, minY = 10000, scaleX = 0, scaleY = 0;
			ComputeDrawBoundaries(ref maxX, ref minX, ref maxY, ref minY, ref scaleX, ref scaleY);

			foreach (Planet p in planets)
			{
				if (p.X() > maxX) maxX = p.X();
				if (p.Y() > maxY) maxY = p.Y();
				if (p.X() < minX) minX = p.X();
				if (p.Y() < minY) minY = p.Y();
			}

			foreach (Planet p in planets)
			{
				int rad = (int)((p.GrowthRate() / 10f) * 60) + 10;
				PointF pt = new PointF((float)((p.X() - minX) * scaleX), pictureBox1.Height - (float)((p.Y() - minY) * scaleY));

				double d = Math.Sqrt((e.X - pt.X) * (e.X - pt.X) + (e.Y - pt.Y) * (e.Y - pt.Y));
                if (d < rad)
                {
                    if (e.Button == MouseButtons.Right && pwd.CurrentGraphPlanet >= 0)
                    {
                        Planet q = planets[pwd.CurrentGraphPlanet];
                        m_ShowDistance = Math.Ceiling(Math.Sqrt((p.X() - q.X()) * (p.X() - q.X()) + (p.Y() - q.Y()) * (p.Y() - q.Y()))).ToString();
                    }
                    else
                    {
                        pwd.CurrentGraphPlanet = p.PlanetID();
                    }
                    pictureBox1.Refresh();
                    return;
                }
			}
		}

        private void pictureBox2_Paint(object sender, PaintEventArgs e)
        {
            e.Graphics.InterpolationMode = System.Drawing.Drawing2D.InterpolationMode.HighQualityBicubic;
            e.Graphics.SmoothingMode = System.Drawing.Drawing2D.SmoothingMode.HighQuality;
            e.Graphics.TextRenderingHint = System.Drawing.Text.TextRenderingHint.AntiAlias;

            pwd.DrawGraph(e.Graphics, pictureBox2.Width, pictureBox2.Height);
        }

        private void RunBot()
        {
            btnRun.Enabled = false;
            btnSend.Enabled = true;
            btnEnd.Enabled = true;

            String cmd = MainForm.CONFIG["global.botdbgcmd"];
            String[] parts = cmd.Split(' ');
            ProcessStartInfo psi = new ProcessStartInfo(parts[0], cmd.Substring(cmd.IndexOf(' ') + 1));
            psi.CreateNoWindow = true;
            psi.RedirectStandardInput = true;
            psi.UseShellExecute = false;

            m_BotProcess = Process.Start(psi);
        }

        int SwitchOwner(int owner)
        {
            if (owner == 0)
                return 0;

            if (numericUpDown1.Value == 1)
                return owner;
            else
                return owner == 1 ? 2 : 1;
        }

        private void SendData()
        {
            String botInput = "clearlog\n";
            botInput += m_TurnNumber + "\nturn\n";
            if (pwd.PendingOrders != null)
            {
                foreach (String order in pwd.PendingOrders)
                    botInput += order + "\n";
                botInput += "pending\n";
            }

            foreach (Planet pl in planets)
            {
                botInput += "P " + pl.X() + " " + pl.Y() + " " + SwitchOwner(pl.Owner()) + " " + pl.NumShips() + " " + pl.GrowthRate() + "\n";
            }

            foreach (Fleet f in fleets)
            {
                botInput += "F " + SwitchOwner(f.Owner()) + " " + f.NumShips() + " " + f.SourcePlanet() + " " + f.DestinationPlanet() + " " + f.TotalTripLength() + " " + f.TurnsRemaining() + "\n";
            }

            botInput += "go\n";

            m_BotProcess.StandardInput.Write(botInput);
            m_BotProcess.StandardInput.Flush();
        }

        private void TerminateBot()
        {
            m_BotProcess.StandardInput.Write("exit\n");
            m_BotProcess.StandardInput.Flush();
            m_BotProcess.WaitForExit();

            m_BotProcess = null;

            btnRun.Enabled = true;
            btnSend.Enabled = false;
            btnEnd.Enabled = false;
        }

        private void ExitBot()
        {
            TerminateBot();
            try
            {
                String debugInfo = File.ReadAllText("andrei.txt");
                if (m_DebugHistory.ContainsKey(m_TurnNumber))
                    m_DebugHistory[m_TurnNumber] = debugInfo;
                else
                    m_DebugHistory.Add(m_TurnNumber, debugInfo);
                ProcessDebugInfo(debugInfo);
                pictureBox1.Refresh();
            }
            catch (Exception) { }
        }

        private void button6_Click(object sender, EventArgs e)
        {
            RunBot();
            SendData();
            ExitBot();
        }

        private void numericUpDown1_ValueChanged(object sender, EventArgs e)
        {
            pwd.MePlayerID = (int)numericUpDown1.Value;
            pictureBox1.Refresh();
        }

        private void button7_Click(object sender, EventArgs e)
        {
            RunBot();
        }

        private void button8_Click(object sender, EventArgs e)
        {
            SendData();
        }

        private void button9_Click(object sender, EventArgs e)
        {
            ExitBot();
        }
    }
}
