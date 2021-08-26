using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Drawing;

namespace BotBenchmark
{
    class PlanetWarsDebugger
    {
        private List<int> m_CenterPlanets;
        private List<int> m_DangerPlanets;
        private List<List<int>> m_Graphs;
        private float[][] m_GraphScores;
        private int m_CurrentGraphPlanet;
        private Dictionary<int, float> m_MyScores, m_EnemyScores;
        private List<Planet> m_Planets;
        private Dictionary<int, Dictionary<int, int>> m_Clusters;
		private const float MIN_SCORE = -1000;
        private double minX, maxX, minY, maxY, scaleX, scaleY;
        private int m_SpearHead;
        private List<float> m_Risk;
        private int m_MePlayerID = 1;
        private int m_TurnNumber;
        private List<String> m_PendingOrders;

        public List<String> PendingOrders
        {
            get { return m_PendingOrders; }
        }

        public int MePlayerID
        {
            get { return m_MePlayerID; }
            set { m_MePlayerID = value; }
        }

        public int CurrentGraphPlanet { set { m_CurrentGraphPlanet = value; } get { return m_CurrentGraphPlanet; } }

        class PlanetFuture
        {
            public String numShips;
            public String numShipsNeeded;
            public String owner;
            public String avail;
            public String safeAvail;
            public String undefendableNum;
        }

        private List<PlanetFuture> future;

        class PlanetDebugInfo
        {
            public PlanetDebugInfo(float t, float d, int r)
            {
                targetScore = t; defenseScore = d; rank = r;
            }
            public float targetScore;
            public float defenseScore;
            public int rank;

            //			public float[] dist;
        }

        private Dictionary<int, PlanetDebugInfo> m_PlanetInfo = new Dictionary<int, PlanetDebugInfo>();

        public PlanetWarsDebugger(List<Planet> planets)
        {
            m_Planets = planets;
            m_MyScores = new Dictionary<int, float>();
            m_EnemyScores = new Dictionary<int, float>();
        }

        public void SetDrawBounderies(double minX, double maxX, double minY, double maxY, double scaleX, double scaleY)
        {
            this.minX = minX;
            this.minY = minY;
            this.maxX = maxX;
            this.maxY = maxY;
            this.scaleX = scaleX;
            this.scaleY = scaleY;
        }

        public void UpdatePlanets()
        {
            m_PlanetInfo.Clear();
            foreach (Planet p in m_Planets)
            {
                m_PlanetInfo.Add(p.PlanetID(), new PlanetDebugInfo(MIN_SCORE, 0, 0));
            }
        }

        public void ProcessDebugSections(int turnNumber, Dictionary<String, String> sections)
        {
            String[] lines;

            m_TurnNumber = turnNumber;

            if (sections.ContainsKey("PENDING ORDERS"))
            {
                lines = sections["PENDING ORDERS"].Split('\n');
                m_PendingOrders = new List<string>();
                foreach (String line in lines)
                    if (line.Trim().Length > 0)
                        m_PendingOrders.Add(line);
            }
            else
                m_PendingOrders = null;

            if (sections.ContainsKey("CLUSTERTARGETS"))
            {
                m_Clusters = new Dictionary<int, Dictionary<int, int>>();
                lines = sections["CLUSTERTARGETS"].Split('\n');
                foreach (String l in lines)
                {
                    if (l.Trim().Length == 0) continue;
                    String [] parts = l.Split(' ');
                    m_Clusters.Add(int.Parse(parts[0]), new Dictionary<int, int>());
                    m_Clusters[int.Parse(parts[0])].Add(int.Parse(parts[1]), int.Parse(parts[2]));
                }                    
            }

            int nCount = 6;

            if (sections.ContainsKey("WORSTCASE"))
            {
                future = new List<PlanetFuture>();
                lines = sections["WORSTCASE"].Split('\n');
                int k = 0;
                PlanetFuture pf = null;
                foreach (String l in lines)
                {
                    if (k == 0)
                    {
                        if (pf != null)
                            future.Add(pf);
                        pf = new PlanetFuture();
                    }

                    if (k == 0)
                        pf.numShips = l;
                    else if (k == 1)
                        pf.numShipsNeeded = l;
                    else if (k == 2)
                        pf.owner = l;
                    else if (k == 3)
                        pf.avail = l;
                    else if (k == 4)
                        pf.safeAvail = l;   
                    else
                        pf.undefendableNum = l;


                    k++;
                    k = k % nCount;
                }
            }

            if (sections.ContainsKey("POWER"))
            {
                String infoPlScores = sections["POWER"];
                lines = infoPlScores.Split('\n');
                if (!m_MyScores.ContainsKey(turnNumber))
                    m_MyScores.Add(turnNumber, float.Parse(lines[1]));
                else
                    m_MyScores[turnNumber] = float.Parse(lines[1]);

                if (!m_EnemyScores.ContainsKey(turnNumber))
                    m_EnemyScores.Add(turnNumber, float.Parse(lines[0]));
                else
                    m_EnemyScores[turnNumber] = float.Parse(lines[0]);
            }

/*            int rank = 1;

            String infoGraph = sections["GRAPHMAT"];
            String[] lines = infoGraph.Split('\n');

            m_GraphScores = new float[m_Planets.Count][];

            int b = 0;
            foreach (String line in lines)
            {
                if (line.Trim().Length == 0) continue;

                m_GraphScores[b] = new float[m_Planets.Count];
                String[] scores = line.Split(' ');
                int k = 0;
                foreach (String sc in scores)
                {
                    if (sc.Length == 0) continue;
                    m_GraphScores[b][k] = float.Parse(sc);
                    k++;
                }
                b++;
            }

            String infoScores = sections["SCORES"];
            lines = infoScores.Split('\n');
            foreach (String line in lines)
            {
                String[] parts = line.Split(' ');
                if (parts.Length < 2) continue;
                int id = int.Parse(parts[0]);
                float score = float.Parse(parts[1]);
                m_PlanetInfo[id].targetScore = score;
                m_PlanetInfo[id].rank = rank;
                rank++;
            }

*/
            if (sections.ContainsKey("GRAPHS"))
            {
                m_Graphs = new List<List<int>>();
                String infoPaths = sections["GRAPHS"];
                lines = infoPaths.Split('\n');

                foreach (String line in lines)
                {
                    if (line.Length == 0) continue;
                    String[] dists = line.Split(' ');
                    int key = -1;
                    foreach (String dist in dists)
                    {
                        if (dist.Length == 0) continue;
                        if (key == -1)
                        {
                            key = int.Parse(dist);
                            m_Graphs.Add(new List<int>());
                        }
                        //                        else
                        m_Graphs[m_Graphs.Count - 1].Add(int.Parse(dist));
                    }
                    m_Graphs[m_Graphs.Count - 1].Reverse();
                }
            }
            else
                m_Graphs = null;

            if (sections.ContainsKey("RISK"))
            {
                m_Risk = new List<float>();
                foreach (String line in sections["RISK"].Split('\n'))
                {
                    if (line.Length == 0) continue;
                    m_Risk.Add(float.Parse(line));
                }
            }

            if (sections.ContainsKey("SPEARHEAD"))
            {
                m_SpearHead = int.Parse(sections["SPEARHEAD"].Split('\n')[0]);
            }

            if (sections.ContainsKey("CENTER"))
            {
                m_CenterPlanets = new List<int>();
                foreach (String ids in sections["CENTER"].Split(' '))
                    if (ids.Trim().Length > 0)
                        m_CenterPlanets.Add(int.Parse(ids.Trim()));
            }
        }

        public void ClearDebugInfo()
        {
            m_CenterPlanets = null;
            m_SpearHead = -1;
            m_Risk = null;
            m_Graphs = null;
            future = null;
            m_Clusters = null;
        }

        public void RenderGlobalDebug(Graphics g, int width, int height)
        {
            Font fnt = new Font(FontFamily.GenericSansSerif, 8); 
            if (m_Graphs != null)
            {
                foreach (List<int> graph in m_Graphs)
                {
                    for (int i = 0; i < graph.Count - 1; ++i)
                    {
                        Planet p = m_Planets[graph[i]];
                        Planet q = m_Planets[graph[i + 1]];
                        PointF qt = new PointF((float)((q.X() - minX) * scaleX), height - (float)((q.Y() - minY) * scaleY));
                        PointF pt = new PointF((float)((p.X() - minX) * scaleX), height - (float)((p.Y() - minY) * scaleY));

                        g.DrawLine(Pens.DarkGray, pt, qt);
//                        g.DrawString(m_GraphScores[p.PlanetID()][q.PlanetID()].ToString("##.00"), fnt, Brushes.DarkGray, (pt.X + qt.X) / 2, (pt.Y + qt.Y) / 2);
                    }
                }
            }

            if (m_Clusters != null)
            {
                foreach (int k in m_Clusters.Keys)
                {
                    foreach (int v in m_Clusters[k].Keys)
                    {

                        Planet p = m_Planets[k];
                        Planet q = m_Planets[v];
                        PointF qt = new PointF((float)((q.X() - minX) * scaleX), height - (float)((q.Y() - minY) * scaleY));
                        PointF pt = new PointF((float)((p.X() - minX) * scaleX), height - (float)((p.Y() - minY) * scaleY));

                        g.DrawLine(Pens.Pink, pt, qt);
                        g.DrawString(m_Clusters[k][v] + "", fnt, Brushes.Pink, (qt.X + pt.X) / 2, (qt.Y + pt.Y) / 2);
                    }
                }
            }
        }

        private String FormatIntList(String lst, int len)
        {
/*            String[] parts = lst.Split(' ');
            int maxL = len;
            foreach (String p in parts)
            {
                if (p.Length > maxL)
                    maxL = p.Length;
            }
            String res = "";
            foreach (String p in parts)
            {
                if (p.Length < maxL)
                    res += new String(' ', maxL - p.Length);
                res += p + " ";                    
            }*/
            return lst;
            //return res;
        }

        public void RenderPlanet(Planet p, Graphics g, int width, int height)
        {
            float maxScore = -10000, minScore = 10000;
            foreach (int key in m_PlanetInfo.Keys)
            {
                if (m_PlanetInfo[key].targetScore > maxScore) maxScore = m_PlanetInfo[key].targetScore;
                if (m_PlanetInfo[key].targetScore < minScore) minScore = m_PlanetInfo[key].targetScore;
            }

            Font fnt = new Font(FontFamily.GenericSansSerif, 8);
            Font fixFont = new Font(FontFamily.GenericMonospace, 9, FontStyle.Bold);
            Font fntp = new Font(FontFamily.GenericSansSerif, 10, FontStyle.Bold);

            int rad = (int)((p.GrowthRate() / 10f) * 60) + 10;
            PointF pt = new PointF((float)((p.X() - minX) * scaleX), height - (float)((p.Y() - minY) * scaleY));

            Brush b = p.Owner() == m_MePlayerID ? Brushes.Red : (p.Owner() == 0 ? Brushes.DarkGray : Brushes.Blue);
            Pen thickpen = new Pen(b, 5);

            PlanetDebugInfo pi = m_PlanetInfo[p.PlanetID()];

            if (m_CurrentGraphPlanet == p.PlanetID() && future != null)
            {
                g.DrawString("      0    1    2    3    4    5    6    7    8    9   10   11   12   13   14   15   16   17   18   19   20   21", fixFont, Brushes.White, 0, 0);
                g.DrawString(FormatIntList(future[p.PlanetID()].numShips, 4), fixFont, Brushes.White, 0, 10);
                g.DrawString(FormatIntList(future[p.PlanetID()].numShipsNeeded, 4), fixFont, Brushes.White, 0, 20);
                g.DrawString(FormatIntList(future[p.PlanetID()].owner, 4), fixFont, Brushes.White, 0, 30);
                g.DrawString(FormatIntList(future[p.PlanetID()].avail, 4) + "", fixFont, Brushes.White, 0, 40);
                g.DrawString(FormatIntList(future[p.PlanetID()].safeAvail, 4) + "", fixFont, Brushes.White, 0, 50);
                g.DrawString(FormatIntList(future[p.PlanetID()].undefendableNum, 4) + "", fixFont, Brushes.White, 0, 60);
            }

            if (pi.targetScore > MIN_SCORE)
            {
                int c = (int)((pi.targetScore - minScore) / (maxScore - minScore) * 200) + 55;
                if (Math.Abs(maxScore - minScore) < 0.01f)
                    c = 200;
                b = new SolidBrush(Color.FromArgb(0, c, 0));
            }

            if (m_DangerPlanets != null && m_DangerPlanets.Contains(p.PlanetID()))
            {
                b = new SolidBrush(Color.Fuchsia);
            }

            if (m_CurrentGraphPlanet == p.PlanetID())
            {
                b = new SolidBrush(Color.Green);
            }

            g.FillEllipse(b, pt.X - rad / 2, pt.Y - rad / 2, rad, rad);

            if (pi.targetScore > MIN_SCORE)
            {
                g.DrawEllipse(thickpen, pt.X - rad / 2, pt.Y - rad / 2, rad, rad);
            }
            else
            {
                g.DrawEllipse(Pens.White, pt.X - rad / 2, pt.Y - rad / 2, rad, rad);
            }

            if (m_Risk != null)
            {
                String ss = m_Risk[p.PlanetID()].ToString("##.#");
                g.DrawString(ss, fntp, Brushes.White, pt.X + (int)(rad * 0.6f), pt.Y + (int)(rad * 0.6f));
            }

            if (m_CenterPlanets != null)
            {
                int idx = m_CenterPlanets.IndexOf(p.PlanetID());
                if (idx > -1)
                {
                    String ss = idx + "";
                    g.DrawString(ss, fntp, Brushes.White, pt.X + (int)(rad * 0.6f), pt.Y + (int)(rad * 0.6f));
                }
            }

            String s = p.NumShips() + "";
            SizeF sz = g.MeasureString(s, fnt);
            g.DrawString(s, fntp, Brushes.White, pt.X - sz.Width / 2, pt.Y - sz.Height / 2);
        }

        public void DrawGraph(Graphics g, int width, int height)
        {
            g.Clear(Color.Black);
            float max = 0, min = 100000;
            foreach (int turn in m_MyScores.Keys)
            {
                if (max < m_MyScores[turn]) max = m_MyScores[turn];
                if (max < m_EnemyScores[turn]) max = m_EnemyScores[turn];
                if (min > m_MyScores[turn]) min = m_MyScores[turn];
                if (min > m_EnemyScores[turn]) min = m_EnemyScores[turn];
            }

            max *= 1.05f;

            int pturn = -1;
            foreach (int turn in m_MyScores.Keys)
            {
                if (!m_MyScores.ContainsKey(turn - 1))
                {
                    continue;
                }
                pturn = turn - 1;

                float pxC = ((float)turn / m_MyScores.Count) * width;
                float myC = height - (m_MyScores[turn] - min) / (max - min) * height;
                float eyC = height - (m_EnemyScores[turn] - min) / (max - min) * height;

                float pxP = ((float)pturn / m_MyScores.Count) * width;
                float myP = height - (m_MyScores[pturn] - min) / (max - min) * height;
                float eyP = height - (m_EnemyScores[pturn] - min) / (max - min) * height;

                g.DrawLine(Pens.Blue, pxP, myP, pxC, myC);
                g.DrawLine(Pens.Red, pxP, eyP, pxC, eyC);

                if (turn == m_TurnNumber)
                {
                    g.DrawLine(Pens.White, pxP, 0, pxP, height);
                }
                pturn = turn;
            }
        }
    }
}
