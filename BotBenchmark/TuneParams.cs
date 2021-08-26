using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.Data;
using System.Drawing;
using System.Linq;
using System.Text;
using System.Windows.Forms;
using System.IO;

namespace BotBenchmark
{
	public partial class TuneParams : Form
	{
		public TuneParams()
		{
			InitializeComponent();
		}

		private Dictionary<String, NumericUpDown> m_Values = new Dictionary<string, NumericUpDown>();

		private void TuneParams_Load(object sender, EventArgs e)
		{
			String[] ps = File.ReadAllLines("params.txt");
			m_Values.Clear();
			int idx = 0;
			int h = 30;
			foreach (String p in ps)
			{
				String[] parts = p.Split('=');
				if (parts.Length != 2) continue;

				Label lbl = new Label();
				lbl.Location = new Point(10, idx * h + 10);
				lbl.Text = parts[0];
				lbl.AutoSize = true;
				this.Controls.Add(lbl);

				NumericUpDown val = new NumericUpDown();
				val.Location = new Point(180, idx * h + 10);
				val.Minimum = -100;
				val.Width = 80;
				val.Value = (decimal)float.Parse(parts[1]);
				val.Increment = 0.01M;
				val.DecimalPlaces = 3;
				this.Controls.Add(val);

				m_Values.Add(parts[0], val);
				idx++;
			}

			this.Height = idx * h + 10 + 60;
		}

		private void button1_Click(object sender, EventArgs e)
		{
			List<String> lines = new List<String>();
			foreach (String k in m_Values.Keys)
			{
				lines.Add(k + "=" + m_Values[k].Value);
			}
			File.WriteAllLines("params.txt", lines.ToArray());
		}
	}
}
