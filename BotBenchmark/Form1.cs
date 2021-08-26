using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.Data;
using System.Drawing;
using System.Linq;
using System.Text;
using System.Windows.Forms;

using System.IO;
using System.Diagnostics;
using System.Threading;
using System.Net;

namespace BotBenchmark
{
	public partial class MainForm : Form
	{
		private String m_EnemyBot;
		private bool m_BenchmarkFinished = true;
		private int m_CurrentTask, m_NumTasks, m_WonTasks;
		private Dictionary<String, int> m_Results;
		private Thread m_Worker;
		private int m_nMaxTasks;
		private bool m_bRecord = false;
		const string HIST_DIR = "BotHistory";
		private string m_TaskOutput;
		private long m_LastDir;
		private String[] MESSAGES = { "ERROR", "LOSE", "WIN", "DRAW" };
		public static Dictionary<String, String> m_Config;
		public const String CFG_FILE = "test_config.txt";
		private int m_OnlineGameResult;
        private List<int> m_OnlineGameResults;
		public int OnlineGameResult { set { m_OnlineGameResult = value; } }
        private const long TICKS_MUL = 1;
		public static Dictionary<String, String> CONFIG { get { return m_Config; } }

		public MainForm()
		{
			InitializeComponent();

			if (!Directory.Exists(HIST_DIR))
				Directory.CreateDirectory(HIST_DIR);

			m_Config = new Dictionary<string, string>();
			try {
				foreach (String line in File.ReadAllLines(CFG_FILE))
				{
					String[] parts = line.Split('=');
					m_Config.Add(parts[0], line.Substring(line.IndexOf('=') + 1));
				}
			}
			catch (Exception)
			{
				m_Config.Add("online.server", "72.44.46.68");
				m_Config.Add("online.port", "995");
				m_Config.Add("online.user", "");
				m_Config.Add("online.pass", "");

				m_Config.Add("global.botcmd", "java -jar MyBot.jar");

				m_Config.Add("vis.x", "0");
				m_Config.Add("vis.y", "0");
				m_Config.Add("vis.width", "320");
				m_Config.Add("vis.height", "320");
				m_Config.Add("vis.state", FormWindowState.Normal.ToString());
			}

            cmbEnemyBot.Items.Add("ONLINE");

            foreach (String bot in Directory.GetFiles("example_bots", "*.jar"))
                cmbEnemyBot.Items.Add(Path.GetFileNameWithoutExtension(bot));

            cmbEnemyBot.SelectedIndex = 0;

			txtUser.Text = m_Config["online.user"];
			txtPass1.Text = m_Config["online.pass"];
		}

		private void OutputReceived(object sender, DataReceivedEventArgs e)
		{
			m_TaskOutput += e.Data;
		}

		public static T[] RandomPermutation<T>(T[] array)
		{
			T[] retArray = new T[array.Length];
			array.CopyTo(retArray, 0);

			Random random = new Random();
			for (int i = 0; i < array.Length; i += 1)
			{
				int swapIndex = random.Next(i, array.Length);
				if (swapIndex != i)
				{
					T temp = retArray[i];
					retArray[i] = retArray[swapIndex];
					retArray[swapIndex] = temp;
				}
			}

			return retArray;
		}

		private void RunAllMaps()
		{
            long ts = m_LastDir = System.DateTime.Now.Ticks / TICKS_MUL;
			if (m_bRecord)
			{
				Directory.CreateDirectory(HIST_DIR + "/" + ts);
			}

			String[] files = Directory.GetFiles("mapsnew");
            if (cbRand.Checked)
                files = RandomPermutation(files);
            else if (cbNo.Checked)
            {
                Array.Sort<String>(files, AlphaNumericCompare);
                files = files.Skip((int)numericUpDown2.Value - 1).ToArray();
            }
            else
                Array.Sort<String>(files, AlphaNumericCompare);
			files = files.Take<String>(m_nMaxTasks).ToArray();

			lock (this)
			{
				m_CurrentTask = 0;
				m_NumTasks = files.Length;
				m_Results = new Dictionary<string, int>();
				m_WonTasks = 0;
			}

			String summary = "";

			foreach (String file in files)
			{
				m_TaskOutput = "";
                ProcessStartInfo psi = new ProcessStartInfo("java", "-jar tools/PlayGame.jar " + file + " " + CONFIG["test.timeout"] + " 200 log.txt \"" + m_Config["global.botcmd"] + "\" \"java -jar " + m_EnemyBot + ".jar\"");
				psi.CreateNoWindow = true;
				psi.RedirectStandardError = true;
				psi.UseShellExecute = false;
				
				if (m_bRecord)
					psi.RedirectStandardOutput = true;

				Process p = Process.Start(psi);
				String s;
				if (m_bRecord)
				{
					p.OutputDataReceived += new DataReceivedEventHandler(OutputReceived);
					p.BeginOutputReadLine();
				}
				bool won = false;
				bool err = false;
				bool draw = true;
				while ((s = p.StandardError.ReadLine()) != null)
				{
					if (s.IndexOf("1 Wins") > -1)
					{
						won = true;
					}
					else if (s.IndexOf("2 Wins") > -1)
					{
						draw = false;
					}
					if (s.IndexOf("WARNING") > -1)
					{
						err = true;
					}
				}
				p.WaitForExit();

				if (m_bRecord)
				{
					String gameRecord = m_TaskOutput;
					String outFile = HIST_DIR + "/" + ts + "/" + Path.GetFileNameWithoutExtension(file) + ".txt";
					File.WriteAllText(outFile, gameRecord);
                    try
                    {
                        File.Copy("andrei.txt", outFile.Replace(".txt", "_dbg.txt"));
                    }
                    catch (Exception) { }
				}

				int res = 0;
				if (won)
					res = 1;
				else if (err)
					res = -1;
				else if (draw)
					res = 2;
				summary += res + " ";

				m_Results.Add(Path.GetFileName(file), res);

				lock (this)
				{
					m_CurrentTask++;
					m_WonTasks += won ? 1 : 0;
				}
			}

			if (m_bRecord)
			{
				File.WriteAllText(HIST_DIR + "/" + ts + "/summary.txt", summary);
				String dest = HIST_DIR + "/" + ts + "/AndreiBot";
                try
                {
                    File.Copy("AndreiBot.jar", dest + ".jar");
                }
                catch (Exception) { }

			}

			lock (this)
			{
				m_BenchmarkFinished = true;
			}
		}

		private void button1_Click(object sender, EventArgs e)
		{
			m_EnemyBot = "example_bots/BullyBot";
			m_bRecord = true;
			StartTasks();
		}

		private void StartTasks()
		{
			m_BenchmarkFinished = false;
			m_nMaxTasks = (int)numericUpDown1.Value;
            btnGo.Enabled = false;
			ThreadStart ts = new ThreadStart(RunAllMaps);
			m_Worker = new Thread(ts);
			timer1.Enabled = true;
			m_Worker.Start();
		}

		private void timer1_Tick(object sender, EventArgs e)
		{
			lock (this)
			{
				if (m_NumTasks > 0)
					progressBar1.Maximum = m_NumTasks;

				progressBar1.Value = m_CurrentTask;

				if (m_BenchmarkFinished)
				{
					timer1.Enabled = false;
                    btnGo.Enabled = true;

					listView1.Items.Clear();

					foreach (String file in m_Results.Keys)
					{
						ListViewItem lvi = new ListViewItem(new string[] { file, MESSAGES[m_Results[file]+1]});
						if (m_bRecord) lvi.Tag = m_LastDir.ToString();
						listView1.Items.Add(lvi);
					}
					listView1.Items[0].Selected = true;

//					textBox1.Text = (((float)m_WonTasks / m_NumTasks) * 100.0f).ToString("##.##") + "%";

					progressBar1.Value = 0;

					RefreshHistory();
					listView2.Items[0].Selected = true;

					if (numericUpDown1.Value == 1)
					{
						String dir = (String)listView1.SelectedItems[0].Tag;
						String file = listView1.SelectedItems[0].SubItems[0].Text;
						String path = HIST_DIR + "/" + dir + "/" + file;

						GameVis gv = new GameVis(this, path);
						gv.Show();
					}
				}
			}
		}

		private void Form1_FormClosing(object sender, FormClosingEventArgs e)
		{
			if (m_Worker != null && m_Worker.IsAlive)
				m_Worker.Abort();

			try
			{
				m_Config["online.user"] = txtUser.Text;
				m_Config["online.pass"] = txtPass1.Text;

				List<String> list = new List<string>();
				foreach (String key in m_Config.Keys)
				{
					list.Add(key + "=" + m_Config[key]);
				}
				File.WriteAllLines(CFG_FILE, list.ToArray());
			}
			catch (Exception)
			{
			}
		}

        private String TrimZeros(String s)
        {
            for (int i = 0; i < s.Length; ++i)
                if (s[i] == 0)
                {
                    return s.Substring(0, i);
                }

            return s;
        }
        private long ParseByIntParts(String s)
        {
            s = s.Trim();
 

            int l = s.Length;
            Console.WriteLine("'" + s + "'   " + l.ToString());
            long res = 0, power = 1;
            for (int k = 0; k < l; k += 8)
            {
                int e = k+8;
                if (e > l)
                    e = l;

                String part = s.Substring(k, e - k);
                Console.WriteLine(k + "  " + e + "   " + part);
                int p = int.Parse(part);
                res += p * power;
                power *= 100000000;
            }
            return res;
        }

        public int AlphaNumericCompare(String x, String y)
		{
			string s1 = x as string;
			string s2 = y as string;

			int len1 = s1.Length;
			int len2 = s2.Length;
			int marker1 = 0;
			int marker2 = 0;

			// Walk through two the strings with two markers.
			while (marker1 < len1 && marker2 < len2)
			{
				char ch1 = s1[marker1];
				char ch2 = s2[marker2];

				// Some buffers we can build up characters in for each chunk.
				char[] space1 = new char[len1];
				int loc1 = 0;
				char[] space2 = new char[len2];
				int loc2 = 0;

				// Walk through all following characters that are digits or
				// characters in BOTH strings starting at the appropriate marker.
				// Collect char arrays.
				do
				{
					space1[loc1++] = ch1;
					marker1++;

					if (marker1 < len1)
					{
						ch1 = s1[marker1];
					}
					else
					{
						break;
					}
				} while (char.IsDigit(ch1) == char.IsDigit(space1[0]));

				do
				{
					space2[loc2++] = ch2;
					marker2++;

					if (marker2 < len2)
					{
						ch2 = s2[marker2];
					}
					else
					{
						break;
					}
				} while (char.IsDigit(ch2) == char.IsDigit(space2[0]));

				// If we have collected numbers, compare them numerically.
				// Otherwise, if we have strings, compare them alphabetically.
				string str1 = new string(space1);
				string str2 = new string(space2);

				int result;

				if (char.IsDigit(space1[0]) && char.IsDigit(space2[0]))
				{
                    long thisNumericChunk = long.Parse(TrimZeros(str1.Trim()));
                    long thatNumericChunk = long.Parse(TrimZeros(str2.Trim()));
					result = thisNumericChunk.CompareTo(thatNumericChunk);
				}
				else
				{
					result = str1.CompareTo(str2);
				}

				if (result != 0)
				{
					return result;
				}
			}
			return len1 - len2;
		}

		private void btn10_Click(object sender, EventArgs e)
		{
			numericUpDown1.Value = 10;
		}

		private void btn100_Click(object sender, EventArgs e)
		{
			numericUpDown1.Value = 200;
		}

		private void button1_Click_1(object sender, EventArgs e)
		{
			m_EnemyBot = "example_bots/RageBot";
			m_bRecord = true;
			StartTasks();
		}

		private void btnAgainstPrev_Click(object sender, EventArgs e)
		{
			if ((!Directory.Exists(HIST_DIR)) ||
				(Directory.Exists(HIST_DIR) && Directory.GetDirectories(HIST_DIR).Length == 0))
			{
				m_EnemyBot = "example_bots/RageBot";
			}
			else
			{
				String[] dirs = Directory.GetDirectories(HIST_DIR);
				Array.Sort(dirs);
				String dir = dirs[dirs.Length - 1];
				String [] files = Directory.GetFiles(dir, "*.jar");
				m_EnemyBot = files[0].Replace(".jar", "");
			}
			m_bRecord = true;
			StartTasks();
		}

		private void RefreshHistory()
		{
			listView2.Items.Clear();
			if (!Directory.Exists(HIST_DIR)) return;
			String[] hist = Directory.GetDirectories(HIST_DIR);
			Array.Sort(hist);
			Array.Reverse(hist);
			foreach (String dir in hist)
			{
				long ticks = long.Parse(Path.GetFileName(dir));
                System.DateTime d = new DateTime(ticks * TICKS_MUL);
				ListViewItem lvi = new ListViewItem(new String[] { d.ToShortDateString() + " " + d.ToShortTimeString(), "" });
				lvi.Tag = Path.GetFileName(dir);
				listView2.Items.Add(lvi);
			}
		}

		private void Form1_Load(object sender, EventArgs e)
		{
			RefreshHistory();
		}

		private void listView2_DoubleClick(object sender, EventArgs e)
		{
			if (listView2.SelectedItems.Count != 1) return;

			listView1.Items.Clear();
			m_LastDir = long.Parse((String)listView2.SelectedItems[0].Tag);
			String[] files1 = Directory.GetFiles(HIST_DIR + "/" + m_LastDir, "map*.txt");
            String[] files2 = Directory.GetFiles(HIST_DIR + "/" + m_LastDir, "game*.txt");
            Array.Sort(files1, AlphaNumericCompare);
            Array.Sort(files2, AlphaNumericCompare);

            IEnumerable<String> files = files1.Concat(files2);

            String[] parts = null;
            try
            {
                String summary = File.ReadAllText(HIST_DIR + "/" + m_LastDir + "/summary.txt");
                parts = summary.Split(' ');
            }
            catch (IOException) { }

			int i = 0, nw = 0, nt = 0;
			foreach (String file in files)
			{
				if (file.Contains("_dbg")) continue;

				int w = parts == null ? -1 : (parts[i].Trim().Length == 0 ? 0 : int.Parse(parts[i]));
                if (w >= 0 && w < 2)
                {
                    nw += w;
                    nt++;
                }
				ListViewItem lvi = new ListViewItem(new string[] { Path.GetFileName(file), MESSAGES[w+1]});
				lvi.Tag = m_LastDir.ToString();
				listView1.Items.Add(lvi);
				i++;
			}

            listView2.SelectedItems[0].SubItems[1].Text = (nt == 0 ? 0 : ((float)nw / (float)nt * 100.0f)).ToString("#0") + "%";
//			textBox1.Text = 
		}

		private void listView1_DoubleClick(object sender, EventArgs e)
		{
			if (listView1.SelectedItems.Count == 1)
			{
				String dir = (String)listView1.SelectedItems[0].Tag;
				String file = listView1.SelectedItems[0].SubItems[0].Text;
				String path = HIST_DIR + "/" + dir + "/" + file;


				/*ProcessStartInfo psi = new ProcessStartInfo("java", "-jar tools/ShowGame.jar");
				psi.RedirectStandardInput = true;
				psi.UseShellExecute = false;
				Process p = Process.Start(psi);
				p.StandardInput.Write(File.ReadAllText(path));
				p.StandardInput.Close();*/

				GameVis gv = new GameVis(this, path);
				gv.Show();
			}
		}

		private void button2_Click(object sender, EventArgs e)
		{
			m_NumTasks = (int)numericUpDown1.Value;
            m_OnlineGameResults = new List<int>();
			timer2.Enabled = true;

            long ts = m_LastDir = System.DateTime.Now.Ticks / TICKS_MUL;
            Directory.CreateDirectory(HIST_DIR + "/" + ts);

			StartOnlineGame();
		}

		private void StartOnlineGame()
		{
            GameVis gv = new GameVis(this,
                "java",
                "-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005 TCP " +
					m_Config["online.server"] + " " +
					m_Config["online.port"] + " " +
					txtUser.Text + " " +
					txtPass1.Text + " " +
					m_Config["global.botcmd"],
					numericUpDown1.Value > 1
					);

            gv.EnableRecording(HIST_DIR + "/" + m_LastDir + "/game" + (m_nMaxTasks - m_NumTasks + 1) + ".txt");
            m_OnlineGameResult = -100;
			gv.Show();
		}

		private void button6_Click(object sender, EventArgs e)
		{
			TuneParams tp = new TuneParams();
			tp.Show();
		}

		private void btn1_Click(object sender, EventArgs e)
		{
			numericUpDown1.Value = 1;
		}

		private void timer2_Tick(object sender, EventArgs e)
		{
			if (m_OnlineGameResult != -100)
			{
                m_OnlineGameResults.Add(m_OnlineGameResult);
                if (m_NumTasks > 1)
                {
                    m_NumTasks--;
                    StartOnlineGame();
                }
                else
                {
                    String sum = "";
                    foreach (int r in m_OnlineGameResults)
                        sum += r + " ";

                    try
                    {
                        File.WriteAllText(HIST_DIR + "/" + m_LastDir + "/summary.txt", sum);
                    }
                    catch (Exception)
                    {
                    }

                    timer2.Enabled = false;

                    RefreshHistory();
                }
			}
		}

        private void btnGo_Click(object sender, EventArgs e)
        {
            if ((String)cmbEnemyBot.SelectedItem == "ONLINE")
            {
                m_NumTasks = (int)numericUpDown1.Value;
                m_nMaxTasks = m_NumTasks;
                m_OnlineGameResults = new List<int>();
                timer2.Enabled = true;

                long ts = m_LastDir = System.DateTime.Now.Ticks / TICKS_MUL;
                Directory.CreateDirectory(HIST_DIR + "/" + ts);

                StartOnlineGame();
            }
            else
            {
                m_EnemyBot = "example_bots\\" + (String)cmbEnemyBot.SelectedItem;
                m_bRecord = true;
                StartTasks();
            }
        }

        private void btnReplay_Click(object sender, EventArgs e)
        {
            String cd = Directory.GetCurrentDirectory();

            OpenFileDialog ofd = new OpenFileDialog();
            if (ofd.ShowDialog() == DialogResult.OK)
            {
                Directory.SetCurrentDirectory(cd);
                GameVis gv = new GameVis(this, ofd.FileName);
                gv.PlayBackOldFmt = true;
                gv.Show();
            }
            else
                Directory.SetCurrentDirectory(cd);
        }

        private void button1_Click_2(object sender, EventArgs e)
        {
            WebClient wc = new WebClient();
            try
            {
                String page = wc.DownloadString(CONFIG["web.gameinfo"] + txtGameID.Text);
                
                String[] lines = page.Split('\n');
                
                bool switchID = false;
                String fileName = null;
                foreach (String line in lines)
                {
                    String[] parts = line.Split('=');
                    if (parts[0] == "player_two" && parts[1] == CONFIG["online.user"])
                        switchID = true;
                    else if (parts[0] == "playback_string")
                    {
                        fileName = "BotDebugMatches/" + txtGameID.Text + ".txt";
                        File.WriteAllText(fileName, parts[1]);
                    }
                }

                if (fileName != null)
                {
                    GameVis gv = new GameVis(this, fileName);
                    gv.PlayBackOldFmt = true;
                    if (switchID) gv.MyPlayerID = 2;
                    gv.Show();
                }
            }
            catch (Exception err)
            {
                MessageBox.Show(this, err.Message, "Error", MessageBoxButtons.OK, MessageBoxIcon.Error);
            }
        }
	}
}
